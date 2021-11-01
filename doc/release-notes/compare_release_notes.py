#!/usr/bin/env python

import json
import requests

# get tags/body manually with curl -H "Accept: application/vnd.github.v3+json" https://api.github.com/repos/IQSS/dataverse/releases |jq '.[] | "\(.tag_name)::\(.body)"'

gh_endpoint = 'https://api.github.com/repos/IQSS/dataverse/releases'
gh_header = {'accept': 'application/vnd.github.v3+json'}

path = './'
md_suffix = '-release-notes.live'

req = requests.get(gh_endpoint, headers=gh_header)
json = req.json()

for i in json:
   # strip leading v from tag with [1:]
   tag = i['tag_name'][1:]
   # mimic dos2unix's default behavior
   body = i['body'].replace("\r\n","\n")
   livefile = path + tag + md_suffix
   of = open(livefile, 'w')
   of.write(body)
   of.close()
