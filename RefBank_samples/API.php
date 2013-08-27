<?php
echo "\n-------\nresult of RefBank ping\n";
$results = file_get_contents('http://localhost:8080/RefBank/rbk/ping');
echo $results;
echo "\n-------\nresult of RefBank name\n";
$results = file_get_contents('http://localhost:8080/RefBank/rbk/name');
echo $results;
echo "\n-------\nresult of RefBank nodes\n";
$results = file_get_contents('http://localhost:8080/RefBank/rbk/nodes');
echo $results;
echo "\n-------\n";
?>