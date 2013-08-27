import urllib.parse
import urllib.request


req = urllib.request.Request(url='http://localhost:8080/RefBank/rbk/ping')
f = urllib.request.urlopen(req)
print('\n-----\nresult of RefBank ping')
print(f.read().decode('utf-8'))

req = urllib.request.Request(url='http://localhost:8080/RefBank/rbk/name')
f = urllib.request.urlopen(req)
print('\n-----\nresult of RefBank name')
print(f.read().decode('utf-8'))

req = urllib.request.Request(url='http://localhost:8080/RefBank/rbk/nodes')
f = urllib.request.urlopen(req)
print('\n-----\nlist of RefBank nodes')
print(f.read().decode('utf-8'))
