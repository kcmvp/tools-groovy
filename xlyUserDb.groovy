#! /usr/bin/env groovy


import java.text.SimpleDateFormat;

if (!args || args.length < 2) {
    println "Please input appid: eg xlyUserDb.groovy filename appid namePattern"
    return -1;
}

def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
GroovyShell shell = new GroovyShell()
def plainText = shell.parse(new File(scriptDir, "core/PlainText.groovy"))
def db = shell.parse(new File(scriptDir, "core/Db.groovy"))

def file = new File(args[0].trim());
def appID = args[1].trim();
def namePattern = null;
if(args.length > 2) namePattern = args[2]

def sqlCon = db.h2Con(appID)

/*
def headerList = ["Date","SDK Version","App ID","UID","Account ID","Platform","Channel",
                  "AccountType","Gender","Age","Game Server","Resolution",
    "OS","Brand","Net Type","Country","Province","Carrier","Extend1",
                  "Extend2","Extend3","Extend4","Extend5"]

*/

sqlCon.execute('''
	create table if not exists userInfo (
	ID INTEGER IDENTITY,
	LOG_TIME timestamp,
	SDK_VERSION varchar(200),
	APP_ID varchar(50),
	UID varchar(50),
	ACCOUNT_ID varchar(50),
	PLATFORM varchar(50),
	CHANNEL varchar(50),
	ACCOUNT_TYPE varchar(10),
	GENDER varchar(10),
	AGE  varchar(2),
	GAME_SERVER varchar(100),
	RESOLUTION varchar(30),
	OS varchar(30),
	BRAND varchar(30),
	NET_TYPE varchar(10),
	COUNTRY varchar(10),
	PROVINCE varchar(50),
	CARRIER varchar(50),
	Extend1 varchar(30),
    Extend2 varchar(30),
    Extend3 varchar(30),
    Extend4 varchar(30),
    Extend5 varchar(2048),
    Extend6 varchar(30),
    Extend7 varchar(30),
    Extend8 varchar(30)
)
''');







def process = { File f, String... filters ->
    plainText.split(f, filters);
}


if (file.exists()) {
    def dataMap = new HashMap();
    def dataList = new ArrayList();
    if (file.isDirectory()) {
        file.eachFileRecurse {
            if(!namePattern || (it.name.indexOf(namePattern) > -1)){
                println "Process file ${it.name} ..."
                dataList.addAll(plainText.split(it, appID));
            }
        }
    } else {
        if(!namePattern || (file.name.indexOf(namePattern) > -1)){
            println "Process file ${file.name} ..."
            dataList.addAll(plainText.split(file, appID));
        }
    }

    def insert = '''insert into userInfo(
	LOG_TIME,SDK_VERSION,APP_ID,UID,
	ACCOUNT_ID,PLATFORM,CHANNEL,ACCOUNT_TYPE,
	GENDER,AGE,GAME_SERVER,RESOLUTION,OS,BRAND,
	NET_TYPE,COUNTRY,PROVINCE,CARRIER,
	Extend1,Extend2,Extend3,Extend4,
    Extend5,Extend6,Extend7,Extend8)
    values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
    '''

    sqlCon.withTransaction {
        sqlCon.withBatch(100, insert){stmt ->
            dataList.each {
                def logTime = new Date(Long.valueOf(it[0]))
                stmt.addBatch(logTime,it[1],it[2],it[3],it[4],it[5],it[6],it[7],it[8],it[9],it[10],
                        it[11],it[12],it[13],it[14],it[15],it[16],it[17],it[18],it[19],it[20],it[21],
                        it[22],it[23],it[24],it[25])
            }

        }
    }
}

sqlCon.close();
