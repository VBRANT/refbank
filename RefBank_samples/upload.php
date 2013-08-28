<?php

function http_put($url, $put) {
    $fh = fopen($put, 'r');
    $fi = stat($put);
    $fl = $fi['size'];
    $c = curl_init();
    curl_setopt($c, CURLOPT_URL, $url);
    curl_setopt($c, CURLOPT_PUT, true);
    curl_setopt($c, CURLOPT_INFILE, $fh);
    curl_setopt($c, CURLOPT_INFILESIZE, $fl);
    curl_setopt($c, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($c, CURLOPT_HEADER, true); 
    curl_setopt($c, CURLOPT_HTTPHEADER, array('Accept: text/plain', 'Accept-Charset: UTF-8', 'Data-Format: BibTeX', 'User-Name: Example'));
    return curl_exec($c);
}

echo "hello from sample uploader\n";

echo http_put('http://localhost:8080/RefBank/upload/', 'Zootaxa_2524.bib');

echo "goodbye from sample uploader\n";
?>