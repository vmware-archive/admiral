from pyVim.connect import SmartConnectNoSSL, Disconnect
from pyVmomi import vim
import argparse
import time


def parse_args():
    parser = argparse.ArgumentParser(
        description='Arguments for adding a distributed portgroup to a distributed virtual switch')
    parser.add_argument('--command',
                        required=True,
                        action='store',
                        help='Command to execute, supported commands are create and delete')
    parser.add_argument('--target',
                        required=True,
                        action='store',
                        help='vCenter ip')
    parser.add_argument('--username',
                        required=True,
                        action='store',
                        help='User name to use when connecting to the host')
    parser.add_argument('--password',
                        required=True,
                        action='store',
                        help='Password to use when connecting to the host')
    parser.add_argument('--datacenter',
                        required=True,
                        action='store',
                        help='Datacenter name')
    parser.add_argument('--dvswitch',
                        required=True,
                        action='store',
                        help='Name of the virtual distributed switch')
    parser.add_argument('--portgroup',
                        required=True,
                        action='store',
                        help='Name of the portgroup to create')
    parser.add_argument('--numports',
                        required=False,
                        type=int,
                        default=128,
                        action='store',
                        help='Number of ports of the portgroup, optional, defaults to 128')
    return parser.parse_args()


def main():
    args = parse_args()
    if args.command != "create" and args.command != "delete":
        raise Exception("Invalid --command argument value: " + args.command + ", supported commands are create and delete")
    connection = SmartConnectNoSSL(host=args.target, user=args.username, pwd=args.password)
    try:
        network_folder = get_network_folder(connection, args)
        if args.command == "create":
            dvs = get_dvs(network_folder, args)
            create(dvs, args)
        elif args.command == "delete":
            delete(network_folder, args)
    finally:
        Disconnect(connection)  


def get_dvs(network_folder, args):
    switch = None
    for d in network_folder.childEntity:
        if d.name == args.dvswitch:
            switch = d
            break
    if switch == None:
        raise Exception("Could not find distributed virtual switch: " + args.dvswitch)
    return switch

def get_network_folder(connection, args):
    dcs = connection.content.rootFolder.childEntity
    dc = None
    for d in dcs:
        if d.name == args.datacenter:
            dc = d
            break
    if dc == None:
            raise Exception("Could not find datacenter: " + args.datacenter)
    return dc.networkFolder
    
    
def create(dvs, args):
    dvsSpec = vim.dvs.DistributedVirtualPortgroup.ConfigSpec()
    dvsSpec.name = args.portgroup
    dvsSpec.type = "earlyBinding"
    dvsSpec.numPorts = args.numports
    task = dvs.CreateDVPortgroup_Task(spec=dvsSpec)
    while task.info.state != "success" and task.info.state != 'error':
        time.sleep(1)
    if task.info.state == 'error':
        raise Exception("Creating a portgroup failed: " + task.info.error.msg)
    print("Successfully created portgroup: " + args.portgroup)

    
def delete(network_folder, args):
    portgroup = None
    for item in network_folder.childEntity:
        if item.name == args.portgroup and item.config.distributedVirtualSwitch.name == args.dvswitch:
            portgroup = item
    if portgroup == None:
        raise Exception("Could not find portgroup with name: " + args.portgroup + " in dv switch: " + args.dvswitch)
    task = portgroup.Destroy()
    while task.info.state != "success" and task.info.state != 'error':
        time.sleep(1)
    if task.info.state == 'error':
        raise Exception("Deleting a portgroup failed: " + task.info.error.msg)
    print("Successfully deleted portgroup: " + args.portgroup)

if __name__ == "__main__":
    main()
