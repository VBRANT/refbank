import urllib.request
with open('Zootaxa_2524.bib') as f:
    source_ref = f.read().encode('utf-8')
req = urllib.request.Request(url='http://localhost:8080/RefBank/upload/')
req.add_header('Accept', 'text/plain')
req.add_header('Accept-Charset', 'UTF-8')
req.add_header('Data-Format', 'BibTeX')
req.add_header('User-Name', 'Example')
req.method = 'PUT'
f = urllib.request.urlopen(req, data=source_ref)
print(f.read())
