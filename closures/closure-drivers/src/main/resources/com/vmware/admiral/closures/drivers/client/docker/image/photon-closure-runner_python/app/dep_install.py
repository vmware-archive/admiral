#!/usr/bin/env python3

import os
import appscript as closure


def main():
    if os.getenv("TRUST_CERTS") is not None:
        os.environ['REQUESTS_CA_BUNDLE'] = './trust.pem'

    closure.preinstall_dependencies()


if __name__ == "__main__":
    main()