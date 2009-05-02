#!/bin/bash
tr -cd 0-9 </dev/urandom | dd bs=1 count=19 2>/dev/null
echo
