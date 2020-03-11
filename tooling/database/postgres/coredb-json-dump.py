#!/usr/bin/python

import sys
import psycopg2
import os
import json
from os import walk
from os.path import isfile, join



###############################################################
#####   Export core database dump in json format
#####   This keep original normalized schemas
#####   It exports also ids in order to join related data
#####   usage : python3 host database user password coredbOutputDirectory
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
######### Extract all databases
######### Return databases
#######################################################
def extractDbs(connection) :
    c = connection.cursor()
    c.execute("SELECT datname FROM pg_database;")
    dbs = c.fetchall()
    c.close()
    return dbs

#####################################
######### Extract data from core db
#####################################

def extract(connection, table_name, columns) :
    extracted = []

    # Check if table exists
    c = connection.cursor()
    c.execute("SELECT EXISTS (SELECT FROM pg_tables WHERE tablename  = '" + table_name + "');")
    table_exists = c.fetchall()
    if table_exists[0][0] :
        cols = ', '.join(columns)
        query = "SELECT " + cols + " FROM " + table_name
        c.execute(query)
        for tupl in c.fetchall() :
            obj = {}
            for idx in range(0, len(columns)) :
                obj[columns[idx]] = tupl[idx]
            extracted.append(obj)

    c.close()
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
        # Avoid template databases
        if db[0] == sys.argv[3] or db[0].find("template") != -1:
            continue
        conn = psycopg2.connect(host=sys.argv[1], port=sys.argv[2], database=db[0], user=sys.argv[4], password=sys.argv[5])
        dumpData = {}
        for table in schema :
            tableName = table[0]
            dumpData[tableName] = extract(conn, tableName, table[1])
            if tableName not in tableRows :
                tableRows[tableName] = 0
            tableRows[tableName] += len(dumpData[tableName])
        exportAsJsonFile(outputDir, db[0], dumpData)

        conn.close()
    print("Has extracted from ", databaseCount, " databases :")
    print(json.dumps(tableRows, indent=4, sort_keys=True))
####################
######### Main
####################

### Check inputs
if len(sys.argv) < 6 :
    print("Please provide PostgreSQL parameters (host, port, database, user and password) and output directory.")
    exit(-1)

# dbsRoot = sys.argv[1]
output = sys.argv[6]
print("Establish connection to PostgreSQL server : ", )
conn = psycopg2.connect(host=sys.argv[1], port=sys.argv[2], database=sys.argv[3], user=sys.argv[4], password=sys.argv[5])


if not os.path.exists(output) :
    print(output, " does not exists... Create it.")
    os.mkdir(output)

dbs = extractDbs(conn)
dump(dbs, output)
