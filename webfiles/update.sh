#! /bin/bash
mv RefBank.war RefBank.zip
unzip -uo RefBank.zip -x *.cnfg *ooter.html
mv RefBank.zip RefBank.war
