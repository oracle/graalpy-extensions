import urllib.request

url = "https://pypi.org/simple/termcolor/"
req = urllib.request.Request(url, headers={
    "Accept": "application/vnd.pypi.simple.v1+json"
})

content = None
with urllib.request.urlopen(req) as response:
    content = response.read().decode('utf-8')
    print("Page content:")
    print(content)


import json
x = json.loads(content)

print("\n\n\n\n")
print(repr(x))
print("==============================================")
print("==============================================")
print("\n\n\n\n")
