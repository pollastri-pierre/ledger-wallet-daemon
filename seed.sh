#!/usr/bin/env bash

##################################################################################################
########    Script to bring WD to a certain state starting from an existing core database:
########
########
########    Usage with SQLite:
########    $> bash seed.sh <path_to_client_database> <path_to_core_database>
########    - client_database: database keeping client's names, public keys (generally located at root project and named `database.db`),
########    - core_database: core database used by the libcore (SQLite and PostgreSQL are supported).
########    Example :
########    $> bash seed.sh . core_data
########
########
########    Usage with PostgreSQL:
########    $> bash seed.sh <path_to_client_database> <pg_host> <pg_port> <pg_user>
########    - client_database: database keeping client's names, public keys (generally located at root project and named `database.db`),
########    - core_database: core database used by the libcore (SQLite and PostgreSQL are supported).
########    Usage Example :
########    $> bash seed.sh . localhost 5432 ledger
##################################################################################################

# Logger
# @params
# $1 message
# $2 level of criticity: 0 => INFO, 1 => WARNING, 2 => ERROR
colors=(34 33 31) #("Blue" "Yellow" "Red")
levels=("INFO" "WARN" "ERROR")
logger () {
	printf "\e[${colors[$2]}m%s" "[${levels[$2]}]"
	printf "\e[39m%s\n" ": $1"
}

argc=$#
client_database=$1
core_database_path=$2
pg_host=$2
pg_port=$3
pg_user=$4

###########################
######### Helpers #########
###########################

# Exit gracefully script
# @params
# $1 reason
abort_execution () {
	printf "\xF0\x9F\x9A\xA8 "
	logger "$1" 2
	exit 1
}

# Check if command is available
# @params
# $1 command name
# $2 level of criticity 0 => OK if missing, 1 => KO if missing
command_available () {
	local cmd=$(command -v $1)
	if ! [ -z "$2" ] && [ $2 == 1 ]; then
		[ -z "$cmd" ] && abort_execution "Please install '$1' then relaunch script."
	else
		if [ -z "$cmd" ]; then
			logger "Command '$1' not found." 1
			return 1
		else
			return 0
		fi
	fi
	return 1
}

# Check if databases are available
database_available () {
  [ "$argc" -lt 2 ] && abort_execution "Missing arguments: at least 2 arguments needed (with SQLite support)."
  [ ! -f "$client_database" ] && abort_execution "Missing client database: '$client_database' do not exist."

	if [ "$argc" -eq 2 ]; then
	  ### Sqlite
	  [ ! -d "$core_database_path" ] && abort_execution "Missing core database: '$core_database_path' do not exist."
	  mode=sqlite
	else
	  ### PostgreSQL
	  command_available psql
	  read -s -p "Enter PG server Password: " pg_pwd
	  mode=postgres
	fi
	return 1
}

####################
###### Checks ######
####################

database_available
logger "Seeding Wallet Daemon with $mode support ..."

command_available python

mkdir -p tmp
logger "Generating JSON files that we will be used to recreate accounts ..."
python3 tooling/database/sqllite/coredb-json-dump.py $core_database_path tmp

logger "Extracting clients public keys ..."
python3 tooling/database/sqllite/extract-wd-pubkeys.py $client_database tmp seed
[ ! -f tmp/clients ] && abort_execution "Failed to extract public keys."

let i=0
while IFS=$'\n' read -r line_data; do
  clients_data[i]="${line_data}"
  ((++i))
done < tmp/clients
[ $((${#clients_data[@]} % 2)) != 0 ] && abort_execution "Clients names and public keys should have equal size."

if [ "$mode" == "sqlite" ]; then
  for ((i=0;i<${#clients_data[@]}/2;i++))
  do
        client_name=${clients_data[$((2*$i))]}
        client_pk="${clients_data[$((2*$i + 1))]}"

        client_file=`echo tmp/$client_name.json`
        [ ! -f $client_file ] && abort_execution "Missing client json file '$client_file'."

        python3 tooling/request-gen.py --creation --sync --print http://localhost:8888 $client_file $client_pk $client_name
  done
fi

printf "\xF0\x9F\x9A\x80   Successfully reseeded Wallet Daemon !"

exit 0