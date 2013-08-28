import urllib.parse
import urllib.request

f = urllib.request.urlopen('http://localhost:8080/RefBank/rbk?action=find&author=curler&date=2010')
print('\n-----\nresult of RefBank hardcoded find')
print(f.read().decode('utf-8'))
