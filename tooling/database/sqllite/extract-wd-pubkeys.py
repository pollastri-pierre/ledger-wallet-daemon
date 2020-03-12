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
    print("Please provide path to walletdaemon database (1) and output file path (2)")
    exit(-1)


dbsRoot =  sys.argv[1]
output =  sys.argv[2]
mode =  sys.argv[3]

if mode == "seed":
    if not os.path.exists(output) :
        print(output, " Does not exist !")
        exit(1)
else:
    if os.path.exists(output) :
        print(output, " Already exists...")
        exit(1)

#####################################
######### Extract data from core db
#####################################

def extract(connection) :
    c = connection.cursor()
    query = "\
            SELECT p.name, u.pub_key \
            FROM pools p, users u \
            WHERE p.user_id = u.id "

    c.execute(query)
    return c.fetchall()

def writeData(path, outputFileName, data) :
    with open(path + '/' + outputFileName, 'w') as outfile:
        for elem in data:
            outfile.write(elem)
            outfile.write("\n")

#################################################
######### Export data to outputDir/fileName.json 
#################################################
def exportAsJsonFile(path, data) :
    if mode == "seed":
        clientNames=[]
        clientPks=[]
        for elem in data:
            clientNames.append(elem[0])
            clientPks.append(elem[1])

        # Generate public keys file
        index=0
        with open(path + '/clients', 'w') as outfile:
            while index < len(clientNames):
                outfile.write(clientNames[index])
                outfile.write("\n")
                outfile.write(clientPks[index])
                outfile.write("\n")
                index+=1
    else:
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

