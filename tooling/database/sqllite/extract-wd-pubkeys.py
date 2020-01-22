#!/usr/bin/python

import sys
import sqlite3
import os
import json
from os import walk
from os.path import isfile, join

#######################
######### Check inputs 
#######################
if len(sys.argv) < 3 :
    print "Please provide path to walletdaemon database (1) and output file path (2) !!!"
    exit(-1)


dbsRoot =  sys.argv[1]
output =  sys.argv[2]

if os.path.exists(output) :
    print(output, " Already exists...")
    exit(1)


#####################################
######### Extract data from core db
#####################################

def extract(connection) :
    extracted = []
    c = connection.cursor()
    query = "\
            SELECT p.name, u.pub_key \
            FROM pools p, users u \
            WHERE p.user_id = u.id "

    c.execute(query)
    return c.fetchall()

#################################################
######### Export data to outputDir/fileName.json 
#################################################
def exportAsJsonFile(path, data) :
    with open(path, 'w') as outfile:
        json.dump(data, outfile, indent=4, sort_keys=True)

############################################
######### Export data to output/dbName.json 
############################################
def dump(dbpath, outputFile) :
    conn = sqlite3.connect(dbpath)
    extr = extract(conn)
    exportAsJsonFile(outputFile, extr)


dump(dbsRoot, output)

