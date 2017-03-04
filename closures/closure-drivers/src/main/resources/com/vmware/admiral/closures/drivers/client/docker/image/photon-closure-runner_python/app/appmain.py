#!/usr/bin/env python3

import datetime
import os
import appscript as closure


def main():
    print ('Script run started at: {0}'.format(datetime.datetime.now()))

    if os.getenv("TRUST_CERTS") is not None:
        os.environ['REQUESTS_CA_BUNDLE'] = '/app/trust.pem'

# set general error handler
    closure.setup_exc_handler(None)

    closure.proceed_with_closure_execution()


if __name__ == "__main__":
    main()
