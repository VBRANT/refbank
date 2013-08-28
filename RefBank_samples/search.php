<?php
echo "\n-------\nresult of RefBank search\n";
$results = file_get_contents('http://localhost:8080/RefBank/rbk?action=find&author=curler&date=2010');
echo $results;
echo "\n-------\n";
?>