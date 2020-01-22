import argparse
import requests
import sys
import os
import json
from os import walk
from os.path import isfile, join
from enum import Enum
import time

##################################################################################################
########    Tool to generate set of requests from libcore database dump fortmated in json format.
########    This script is generating requests to recreate the whole structure from scratch
########    This script can also generate read requests in order to run for benches for instance.
########    Usage Exemple : 
# $> python3 ./pool_account_wallet_creation.py http://localhost:8888 data/coredb/my-pool.json 12340000PUBKEY000000004321 targetpoolname --sync --creation --print
##################################################################################################

#######################
######### Check inputs 
#######################
parser = argparse.ArgumentParser()
parser.add_argument("host", help="Target Host for requests generation http://hosname:port ")
parser.add_argument("input", help="Input json file where pools, wallets and account structures can be found")
parser.add_argument("pubKey", help="Public key of user account on wallet daemon (For athent purpose, generate header)")
parser.add_argument("pool", help="Pool name under which queries has to be executed (target)")
parser.add_argument("--sync", help="If sync pool operations after creations is needed", action="store_true")
parser.add_argument("--print", help="Print generated queries", action="store_true")
parser.add_argument("--dry_run", help="No requests will be sent if dry run is enabled", action="store_true")
parser.add_argument("--creation", help="Manage creations, generate requests for accounts creations", action="store_true")
parser.add_argument("--bench", help="Expect file output name generate request and write it into the file on k6 compatible script format")

args = parser.parse_args()

## Host
host = args.host
print("Host to reach : ", host)

## Pool
pool = args.pool
print("Pool creation : ", pool)

## pubKey
pubKey = args.pubKey
print("Public key : ", pubKey)

## Print queries ?
printQueries = args.print
print("Print queries :", printQueries)

## execute queries ?
dryRun = args.dry_run
print("Dry run :", dryRun)

## Output file ? 
bench = False
outputBenchFile = None
if args.bench :
    bench = True
    outputBenchFile = args.bench
print("Print to file : ", bench, " / Path :", outputBenchFile)

## Input json 
input = {}
if args.input:
    with open(args.input, 'r') as f:
        input = json.load(f)
else : 
    print("Input file does not exists : ", args.input, sep='')

##############################
##### Query class def 
##############################
class HTTPMethod(Enum):
    GET = 1
    POST = 2
    DELETE = 3

class HTTPRequest:
  def __init__(self, method, route, body, pubKey):
    self.method = method
    self.route = route
    self.body = body
    self.pubKey = pubKey


def to_json(httpRequest, pretty) : 
    if pretty :
        return json.dumps(vars(httpRequest), indent=4)
    else :
        return json.dumps(vars(httpRequest))

def full_route(route) :
    return host + route

def execute(request) :
    headers = {'user-agent': 'wd-tool/0.0.1', 'pubKey':pubKey, 'content-type':'application/json; charset=utf8'}
    url = full_route(request.route)
    
    if dryRun or printQueries :
        print("Executing : ", url)
        print("Headers : ", headers)
        print("Body : ", request.body)
        print("Method : ", request.method)
    if not dryRun :
        if(request.method == "GET") :
            r = requests.get(url, headers=headers, parameters=request.body)
        elif (request.method == "POST") :
            r = requests.post(url, headers=headers, data=request.body)
        else :
            print("ERROR : method is not managed ", request.method)
        print(url+" -> Status code : ", r.status_code)
        time.sleep(.300)

##############################
##### Payloads creation 
##############################

def create_pool_payload(pool_name) :
    obj = {}
    obj['pool_name'] = pool_name
    return json.dumps(obj)

def create_pool_request(pool_name) :
    body = create_pool_payload(pool_name)
    return HTTPRequest(HTTPMethod.POST.name, "/pools", body, pubKey)

def create_wallet_payload(wallet) :
    obj = {}
    obj['wallet_name'] = wallet['name']
    obj['currency_name'] = wallet['currency_name']
    return json.dumps(obj)

def create_wallet_request(wallet) :
    body = create_wallet_payload(wallet)
    return HTTPRequest(HTTPMethod.POST.name, "/pools/"+pool+"/wallets", body, pubKey)


def create_account_payload(account, extended_key) :
    obj = {
        'account_index' : account['idx'],
        'derivations' : [{
            "path": "44'/37'/8'",
			"owner": "main",
			"extended_key": extended_key
        }]

    }
    return json.dumps(obj)


def create_account_request(wallet_name, account, extended_key) :
    body = create_account_payload(account, extended_key)
    return HTTPRequest(HTTPMethod.POST.name, "/pools/"+pool+"/wallets/"+wallet_name+"/accounts/extended", body, pubKey)

def create_wallets(walletPerUid) :
    for wallet in input["wallets"] :
        uid = wallet['uid']
        obj = create_wallet_request(wallet)
        execute(obj)

def walletPerUid() :
    walletPerUid = {}
    for wallet in input["wallets"] :
        uid = wallet['uid']
        walletPerUid[uid] = wallet
    return walletPerUid

def ethAccPerUid() :
    ethAccountPerUid = {}
    for ethAccount in input["ethereum_accounts"] :
        uid = ethAccount['uid']
        ethAccountPerUid[uid] = ethAccount
    return ethAccountPerUid


def create_pool() :
    obj = create_pool_request(pool)
    execute(obj)

def create_eth_acccounts(walletPerUid) :
    for ethAccount in input["ethereum_accounts"] :
        wallet_name = walletPerUid[ethAccount["wallet_uid"]]["name"]
        req = create_account_request(wallet_name, ethAccount, ethAccount['address'])
        execute(req)

def create_btc_acccounts(walletPerUid) :
    for btcAccount in input["bitcoin_accounts"] :
        wallet_name = walletPerUid[btcAccount["wallet_uid"]]["name"]
        req = create_account_request(wallet_name, btcAccount, btcAccount['xpub'])
        execute(req)

def sync_pool() : 
    request = HTTPRequest(HTTPMethod.POST.name, "/pools/"+pool+"/operations/synchronize", {}, pubKey)
    execute(request)

def bench_requests() :
    bench_requests = []
    walletPerUid = {}

    for wallet in input["wallets"] :
        uid = wallet['uid']
        walletPerUid[uid] = wallet
        wallet_name = wallet["name"]
        bench_requests.append(["GET","/pools/"+pool+"/wallets/"+wallet_name, {} , pubKey])

    for account in input["bitcoin_accounts"] :
        wallet_name = walletPerUid[account["wallet_uid"]]["name"]
        idx = str(account['idx'])
        prefix = "/pools/"+pool+"/wallets/"+wallet_name+"/accounts/"+idx

        bench_requests.append(["GET",prefix+"/balance", {} , pubKey])
        bench_requests.append(["GET",prefix+"/operations?full_op=0", {} , pubKey])
        bench_requests.append(["GET",prefix+"/operations?full_op=1", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=DAY", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=WEEK", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=MONTH", {} , pubKey])

    for account in input["ethereum_accounts"] :
        wallet_name = walletPerUid[account["wallet_uid"]]["name"]
        idx = str(account['idx'])
        prefix = "/pools/"+pool+"/wallets/"+wallet_name+"/accounts/"+idx

        bench_requests.append(["GET",prefix+"/balance", {} , pubKey])
        bench_requests.append(["GET",prefix+"/operations?full_op=0", {} , pubKey])
        bench_requests.append(["GET",prefix+"/operations?full_op=1", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=DAY", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=WEEK", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=MONTH", {} , pubKey])

    ethAccountPerUid = ethAccPerUid()

    for erc20account in input["erc20_accounts"] :
        account = ethAccountPerUid[erc20account["ethereum_account_uid"]]
        address = erc20account["contract_address"]
        wallet_name = walletPerUid[account["wallet_uid"]]["name"]
        idx = str(account['idx'])

        prefix = "/pools/"+pool+"/wallets/"+wallet_name+"/accounts/"+idx
        
        #List tokens 
        bench_requests.append(["GET",prefix+"/tokens", {} , pubKey])
        bench_requests.append(["GET",prefix+"/balance?contract="+address, {} , pubKey])
        prefix = prefix+"/tokens/"+address
        
        bench_requests.append(["GET",prefix+"/operations?full_op=0", {} , pubKey])
        bench_requests.append(["GET",prefix+"/operations?full_op=1", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=DAY", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=WEEK", {} , pubKey])
        bench_requests.append(["GET",prefix+"/history?start=2019-12-01T00:00:00Z&end=2020-01-31T23:59:59Z&time_interval=MONTH", {} , pubKey])

    return bench_requests




if args.creation :
    # Join accounts with their respective wallets    
    walletPerUid = walletPerUid()
    ###### Create Pool ######
    create_pool()
    ##### Create Wallets ####
    create_wallets(walletPerUid)

    ##### Create ETH accounts ####
    create_eth_acccounts(walletPerUid)

    ##### Create BTC accounts ####
    create_btc_acccounts(walletPerUid)

if args.sync :
    sync_pool()

if bench :
    print("Export bench queries to "+outputBenchFile )
    bench_requests = bench_requests()
    if printQueries :
        json.dumps(bench_requests, indent=4, sort_keys=True)
    with open(outputBenchFile, 'w') as f:
        json.dump(bench_requests, f, indent=4, sort_keys=True)

