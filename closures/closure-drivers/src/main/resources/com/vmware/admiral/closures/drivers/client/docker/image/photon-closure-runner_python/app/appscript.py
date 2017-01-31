#!/usr/bin/env python3

import io
import traceback
import datetime
import time
import os.path
import subprocess
import os
import json
import sys
import importlib
import zipfile
from urllib.parse import urlparse
import requests

SRC_DIR = './user_scripts'
SRC_REQ_FILE = 'requirements.txt'


def save_source_in_file(closure_description, module_name):
    src_file = None
    try:
        if not os.path.exists(SRC_DIR):
            os.makedirs(SRC_DIR)
        src_file = open(SRC_DIR + os.sep + module_name + '.py', "w")
    except Exception as err:
        sys.stderr.write('ERROR: Unable to save source file %sn' % str(err))
        raise err
    else:
        # print 'Saving python script: {}'.format(closure_description['source'])
        src_file.write(closure_description['source'])
    finally:
        src_file.close()


def save_dependencies(closure_description):
    if 'dependencies' in closure_description:
        dependencies = closure_description['dependencies']
        if dependencies:
            print ('Saving dependencies: %s' % str(dependencies))
            try:
                src_file = open(SRC_DIR + os.sep + SRC_REQ_FILE, "w")
            except Exception as err:
                sys.stderr.write('ERROR: Unable to save dependency file %s' % str(err))
                raise err
            else:
                src_file.write(dependencies)
            finally:
                src_file.close()


def install_dependencies():
    requirements_path = SRC_DIR + os.sep + SRC_REQ_FILE
    if os.path.exists(requirements_path):
        try:
            subprocess.run(['pip3', 'install', '-r', requirements_path],
                           check=True, stderr=subprocess.PIPE)
        except subprocess.CalledProcessError as e:
            print ('Unable to install dependencies: %s' % str(e.stderr))
            sys.stderr.write('ERROR: Unable to install dependencies. Reason: %s' % str(e.stderr))
            raise Exception('Unable to install dependencies. Reason: %s' % str(e.stderr))
        print ('Dependencies installed.')
    print ('No dependencies found.')


def patch_results(outputs, closure_semaphore, token):
    # print 'Results to be patched: {}'.format(result)
    closure_uri = os.environ['TASK_URI']
    headers = {'Content-type': 'application/json',
               'Accept': 'application/json',
               'x-xenon-auth-token': token
               }
    state = "FINISHED"
    data = {
        "state": state,
        "closureSemaphore": closure_semaphore,
        "outputs": outputs
    }
    patch_resp = requests.patch(closure_uri, data=json.dumps(data), headers=headers)
    if patch_resp.ok:
        print ('Script run state: ' + state)
    else:
        patch_resp.raise_for_status()


class Context:
    def __init__(self, closure_uri, closure_semaphore, inputs):
        self.inputs = inputs
        self.outputs = {}
        self.closure_semaphore = closure_semaphore
        self.closure_uri = closure_uri

    def initialize(self, token):
        def execute_delegate(link, operation, body, handler=None):
            headers = {'Content-type': 'application/json',
                       'Accept': 'application/json',
                       'x-xenon-auth-token': token
                       }
            op = operation.upper()
            target_uri = build_closure_description_uri(self.closure_uri, link)
            if op == 'GET':
                resp = requests.get(target_uri, stream=True)
            elif op == 'POST':
                resp = requests.post(target_uri, data=json.dumps(body), headers=headers)
            elif op == 'PATCH':
                resp = requests.patch(target_uri, data=json.dumps(body), headers=headers)
            elif op == 'PUT':
                resp = requests.put(target_uri, data=json.dumps(body), headers=headers)
            elif op == 'DELETE':
                resp = requests.delete(target_uri, headers=headers)
            else:
                print ('Unsupported operation on ctx.execute()!', operation)
                patch_failure(self.closure_semaphore, Exception('Unsupported operation: ', operation), token)
                return
            if handler is not None:
                handler(resp)

        return execute_delegate


def execute_saved_source(closure_uri, inputs, closure_semaphore, module_name, handler_name):
    # print 'Calling python script...{}{}' \
    #     .format(os.path.join(SRC_DIR, module_name, handler_name), '.py')
    print ('Script run logs:')
    print ('*******************')
    try:
        sys.path.append(os.path.abspath(SRC_DIR))
        # context = {
        #     "inputs": inputs,
        #     "outputs": {}
        # }
        token = os.environ['TOKEN']
        context = Context(closure_uri, closure_semaphore, inputs)
        context.execute = context.initialize(token)
        os.environ['TOKEN'] = ''
        module = importlib.import_module(module_name)
        handler = getattr(module, handler_name)
        handler(context)
        print ('*******************')
        patch_results(context.outputs, closure_semaphore, token)
    except Exception as ex:
        print ('*******************')
        print ('Script run failed with: ', ex)
        patch_failure(closure_semaphore, ex, token)
        exit(1)
    finally:
        print ('Script run completed at: {0}'.format(datetime.datetime.now()))


def download_and_save_source(source_url, module_name, closure_description, skip_execution):
    chunk_size = 10 * 1024
    if not os.path.exists(SRC_DIR):
        os.makedirs(SRC_DIR)
    # print 'Downloading source from: ', source_url
    resp = requests.get(source_url, stream=True)
    content_type = resp.headers['content-type']
    if resp.status_code != 200:
        raise Exception('Unable to fetch script source from: ', source_url)
    # print 'Type of source content: ', content_type
    if 'application/zip' in content_type or 'application/octet-stream' in content_type:
        print ('Processing ZIP source file...')
        sip_content = zipfile.ZipFile(io.BytesIO(resp.content))
        sip_content.extractall(SRC_DIR)
    else:
        with open(os.path.join(SRC_DIR, module_name) + '.py', 'wb') as file_dest:
            for chunk in resp.iter_content(chunk_size):
                file_dest.write(chunk)
        if skip_execution:
            save_dependencies(closure_description)


def create_entry_point(closure_description):
    handler_name = closure_description['name']
    if 'entrypoint' in closure_description:
        entry_point = closure_description['entrypoint']
        if entry_point:
            entries = entry_point.rsplit('.', 1)
            return entries[0], entries[1]
        else:
            return 'index', handler_name
    else:
        print ('Entrypoint is empty. Will use closure name for a handler name: ' + handler_name)
        return 'index', handler_name


def proceed_with_closure_description(closure_uri, closure_desc_uri, inputs, closure_semaphore, skip_execution):
    # print 'Downloading closure description from: {}'.format(closure_desc_uri)
    headers = {'Content-type': 'application/json',
               'Accept': 'application/json',
               'x-xenon-auth-token': os.environ['TOKEN']
               }
    closure_desc_response = requests.get(closure_desc_uri, headers=headers)
    if closure_desc_response.ok:
        closure_description = json.loads(closure_desc_response.content.decode('utf-8'))
        (module_name, handler_name) = create_entry_point(closure_description)
        if 'sourceURL' in closure_description:
            source_url = closure_description['sourceURL']
            if source_url:
                download_and_save_source(source_url, module_name, closure_description, skip_execution)
            else:
                save_source_in_file(closure_description, module_name)
                if skip_execution:
                    save_dependencies(closure_description)
        else:
            save_source_in_file(closure_description, module_name)
            if skip_execution:
                save_dependencies(closure_description)

        if skip_execution:
            install_dependencies()
        else:
            execute_saved_source(closure_uri, inputs, closure_semaphore, module_name, handler_name)
    else:
        closure_desc_response.raise_for_status()


def build_closure_description_uri(closure_uri, closure_desc_link):
    parsed_obj = urlparse(closure_uri)
    return parsed_obj.scheme + "://" + parsed_obj.netloc + closure_desc_link


def patch_closure_started(closure_uri, closure_semaphore):
    headers = {'Content-type': 'application/json',
               'Accept': 'application/json',
               'x-xenon-auth-token': os.environ['TOKEN']
               }
    state = "STARTED"
    data = {
        "state": state,
        "closureSemaphore": closure_semaphore
    }
    patch_resp = requests.patch(closure_uri, data=json.dumps(data), headers=headers)
    if not patch_resp.ok:
        patch_resp.raise_for_status()


def is_blank(my_string):
    return not (my_string and my_string.strip())


def proceed_with_closure_execution(skip_execution=False):
    closure_uri = os.environ['TASK_URI']

    if is_blank(closure_uri):
        print ('TASK_URI environment variable is not set. Aborting...')
        return

    # print 'Downloading closure from: {0}'.format(closure_uri)
    headers = {'Content-type': 'application/json',
               'Accept': 'application/json',
               'x-xenon-auth-token': os.environ['TOKEN']
               }
    closure_response = requests.get(closure_uri, headers=headers)
    if closure_response.ok:
        closure_data = json.loads(closure_response.content.decode('utf-8'))
        closure_semaphore = closure_data['closureSemaphore']
        if not skip_execution:
            # reinit general error handler
            setup_exc_handler(closure_semaphore)
            patch_closure_started(closure_uri, closure_semaphore)

        if 'inputs' in closure_data:
            closure_inputs = closure_data['inputs']
        else:
            closure_inputs = {}
        closure_desc_link = closure_data['descriptionLink']
        closure_desc_uri = build_closure_description_uri(closure_uri, closure_desc_link)
        proceed_with_closure_description(closure_uri, closure_desc_uri, closure_inputs, closure_semaphore, skip_execution)

    else:
        closure_response.raise_for_status()


def patch_failure(closure_semaphore, error, token=None):
    closure_uri = os.environ['TASK_URI']
    state = "FAILED"
    headers = {'Content-type': 'application/json',
               'Accept': 'application/json',
               'x-xenon-auth-token': token
               }
    if closure_semaphore is None:
        data = {
            "state": state,
            "errorMsg": repr(error)
        }
    else:
        data = {
            "state": state,
            "closureSemaphore": closure_semaphore,
            "errorMsg": repr(error)
        }

    patch_resp = requests.patch(closure_uri, data=json.dumps(data), headers=headers)
    if patch_resp.ok:
        print ('Script run state: ' + state)
    else:
        patch_resp.raise_for_status()


def setup_exc_handler(closure_semaphore):
    def handle_exception(exc_type, exc_value, exc_traceback):
        error = traceback.format_exception(exc_type, exc_value, exc_traceback)
        print ('Exception occurred: ', error)
        patch_failure(closure_semaphore, error, os.environ['TOKEN'])

    sys.excepthook = handle_exception


def preinstall_dependencies():
    print ('Installing dependencies started at: {0}'.format(datetime.datetime.now()))
    proceed_with_closure_execution(True)
    print ('Dependencies installed at: {0}'.format(datetime.datetime.now()))
