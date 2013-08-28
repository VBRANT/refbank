Simple samples for invoking RefBank
===================================

This folder contains several sample scripts and one sample of data to show
how you can invoke RefBank programmatically.

The scripts are _very_ simple, all values are hard coded. In 'production' use
we would recommend you replace RefBank's URL with a variable, for example.

David King <David.King@open.ac.uk> and Guido Sautter <sautter@ipd.uka.de>
for ViBRANT <http://vbrant.eu//>, August 2013

License: GPLv2 <http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt>

Files
-----

API.[php/py/sh] - three simple calls to a RefBank service. The first is a ping
response, should return an empty nodes element. The second should return a
nodes element with the name of the RefBank service being called. The third
should return a node set of all RefBank nodes defined to the RefBank service
being called. This set will always include the home node even if no remote
nodes have been defined.

search.[php/py/sh] - a simple search of RefBank using author name and year of
publication as the search terms. Chosen because these are the most typical
terms used.

upload.[php/py/sh] - a simple example of loading a file, Zootaxa_2524.bib,
into RefBank. Note, a file can only be loaded once into RefBank as exact
duplicates are detected and rejected on load. You will see the appropriate
messages confirming this behaviour if you attempt to load the sample more than
once.

Zootaxa_2524.bib - a sample reference to load into RefBank. The refernce is in
bibtex format.



