#!/usr/bin/python

import sys
import sqlite3
import os
import json
from os import walk
from os.path import isfile, join



###############################################################
#####   Export core database dump in json format
#####   This keep original normalized schemas
#####   It exports also ids in order to join related data    
#####   usage : python3 coredb-json-dump.py coredbOutputDirectory
#####
###############################################################

############################
######### Schema to extract 
############################
schema = [
    ("pools", ["name"]),
    ("wallets", ["uid", "name", "currency_name", "pool_name"]),
    ("bitcoin_accounts", ["wallet_uid", "idx", "xpub"]),
    ("erc20_accounts", ["ethereum_account_uid", "contract_address"]),
    ("ethereum_accounts", ["uid", "wallet_uid", "idx", "address"]),
    ("ripple_accounts", ["wallet_uid", "idx", "address"]),
    ("tezos_accounts", ["wallet_uid", "idx", "public_key"]),
]

#######################################################
######### Extract paths from root db path 
######### Return dbFilname and the full path as tuple
#######################################################
def extractDbPaths(dbcRoot) :
    dbs = []
    for dbdir in os.listdir(dbsRoot) :
        if not os.path.isfile(dbdir) :
            curdir = join(join(dbsRoot,dbdir),"databases")      
            for dbfile in os.listdir(curdir) :
                curfile = join(curdir,dbfile)
                if os.path.isfile(curfile) :
                    dbs.append((dbfile,curfile))
                else : print(curfile, "not a dir")
    return dbs


#####################################
######### Extract data from core db
#####################################

def extract(connection, table_name, columns) :
    extracted = []
    c = connection.cursor()
    cols = ', '.join(columns)
    query = "SELECT " + cols + " FROM " + table_name
    c.execute(query)
    for tupl in c.fetchall() :
        obj = {}
        for idx in range(0, len(columns)) : 
            obj[columns[idx]] = tupl[idx]
        extracted.append(obj)
    return extracted

#################################################
######### Export data to outputDir/fileName.json 
#################################################
def exportAsJsonFile(outputDir, fileName, data) :
    outputJson = join(outputDir,fileName+".json")
    with open(outputJson, 'w') as outfile:
        json.dump(data, outfile, indent=4, sort_keys=True)

############################################
######### Export data to output/dbName.json 
############################################
def dump(dbs, outputDir) :
    databaseCount = 0
    tableRows = {}
    for db in dbs :        
        databaseCount += 1
        conn = sqlite3.connect(db[1])
        dumpData = {}
        for table in schema :
            tableName = table[0]
            dumpData[tableName] = extract(conn, tableName, table[1])
            if tableName not in tableRows :
                tableRows[tableName] = 0
            tableRows[tableName] += len(dumpData[tableName])
        exportAsJsonFile(outputDir, db[0], dumpData)
    
    print("Has extracted from ", databaseCount, " databases :")
    print(json.dumps(tableRows, indent=4, sort_keys=True))
    
####################
######### Main 
####################

### Check inputs 
if len(sys.argv) < 3 :
    print("Please provide root database path of core database (1) and output directory (2) !!!")
    exit(-1)

dbsRoot = sys.argv[1]
output = sys.argv[2]

if not os.path.exists(output) :
    print(output, " does not exists... Create it.")
    os.mkdir(output)

dbsPaths = extractDbPaths(dbsRoot)
print("Looking for SQLite database file on paths : ", json.dumps(dbsPaths, indent=4, sort_keys=True))
dump(dbsPaths, output)
