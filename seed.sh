#!/usr/bin/env bash

##################################################################################################
########    Script to bring WD to a certain state starting from an existing core database:
########
########
########    Usage with SQLite:
########    $> bash seed.sh <daemon_endpoint> <path_to_client_database> <path_to_core_database>
########    - client_database: database keeping client's names, public keys (generally located at root project and named `database.db`),
########    - core_database: core database used by the libcore (SQLite and PostgreSQL are supported).
########    Example :
########    $> bash seed.sh . core_data
########
########
########    Usage with PostgreSQL:
########    $> bash seed.sh <daemon_endpoint> <path_to_client_database> <pg_host> <pg_port> <pg_user>
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
daemon_endpoint=$1
client_database=$2
core_database_path=$3
pg_host=$3
pg_port=$4
pg_user=$5

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
  [ "$argc" -lt 3 ] && abort_execution "Missing arguments: at least 3 arguments needed (with SQLite support)."
  [ ! -f "$client_database" ] && abort_execution "Missing client database: '$client_database' do not exist."

	if [ "$argc" -eq 3 ]; then
	  ### Sqlite
	  [ ! -d "$core_database_path" ] && abort_execution "Missing core database: '$core_database_path' do not exist."
	  mode=sqlite
	else
	  ### PostgreSQL
	  command_available psql
	  [ ! -f ~/.pgpass ] && abort_execution "Could not find '~/.pgpass'."
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

if [ "$mode" == "sqlite" ]; then
  logger "Using SQLite mode ..."
  logger "Generating JSON files that we will be used to recreate accounts ..."
  python3 tooling/database/sqllite/coredb-json-dump.py $core_database_path tmp
else
  logger "Using PostgreSQL mode ..."
  params=`cat ~/.pgpass`
  IFS=':'
  read -ra pg_params <<< "$params" # str is read into an array as tokens separated by IFS
  logger "Generating JSON files that we will be used to recreate accounts ..."
  echo "python3 tooling/database/postgres/coredb-json-dump.py ${pg_params[0]} ${pg_params[1]} ${pg_params[2]} ${pg_params[3]} ${pg_params[4]} tmp"
  python3 tooling/database/postgres/coredb-json-dump.py ${pg_params[0]} ${pg_params[1]} ${pg_params[2]}  ${pg_params[3]} ${pg_params[4]} tmp
fi

logger "Extracting clients public keys ..."
python3 tooling/database/sqllite/extract-wd-pubkeys.py $client_database tmp seed
[ ! -f tmp/clients ] && abort_execution "Failed to extract public keys."

let i=0
while IFS=$'\n' read -r line_data; do
  clients_data[i]="${line_data}"
  ((++i))
done < tmp/clients

[ $((${#clients_data[@]} % 2)) != 0 ] && abort_execution "Clients names and public keys should have equal size."

for ((i=0;i<${#clients_data[@]}/2;i++))
  do
        client_name=${clients_data[$((2*$i))]}
        client_pk="${clients_data[$((2*$i + 1))]}"
        client_file=`echo tmp/$client_name.json`

        [ ! -f $client_file ] && abort_execution "Missing client json file '$client_file'."

        python3 tooling/request-gen.py --creation --sync --print "${daemon_endpoint}" $client_file $client_pk $client_name || abort_execution "Failed to generate '$client_file'."

        client_name=""
        client_pk=""
        client_file=""
  done

printf "\xF0\x9F\x9A\x80   Successfully reseeded Wallet Daemon !"

# Remove tmp directory
rm -rf tmp
exit 0
