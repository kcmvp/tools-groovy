#! /usr/bin/env groovy

import groovy.text.*
import org.codehaus.groovy.runtime.StringBufferWriter

def currentPath = new File(getClass().protectionDomain.codeSource.location.path).parent
GroovyShell groovyShell = new GroovyShell()
def shell = groovyShell.parse(new File(currentPath, "../../core/Shell.groovy"))
def logback = groovyShell.parse(new File(currentPath, "../../core/Logback.groovy"))
def osBuilder = groovyShell.parse(new File(currentPath, "../os/osBuilder.groovy"))

def logger = logback.getLogger("infra-zk")

def CONFIG_FOLDER = "zookeeper"
def DEPLOYABLE_HOME = "ZK_HOME"
def CONFIG_FILE_NAME = "zookeeperCfg.groovy"

def cfg = { config ->
    osBuilder.generateCfg(config, CONFIG_FOLDER)
}
def buildOs = { config ->
    osBuilder.etcHost(config.settings.hosts)
}

def mkdir = { config, host ->

    if (config.settings.hosts.contains(host)) {
        def dirs = config.flatten().findAll { it -> it.key.toUpperCase().indexOf("DIR") > -1 }.collect {
            it.value
        }.flatten()
        osBuilder.mkdirs(host, dirs)
    } else {
        logger.error "${host} is not in the server list : ${config.setting.hosts.toString()}"
    }

}
def deploy = { config, deployable, host ->

    if (config.settings.hosts.contains(host)) {

        def consolidated = osBuilder.consolidate(deployable, CONFIG_FOLDER, host)
        if (consolidated) {

            def rt = osBuilder.deploy(consolidated, host, DEPLOYABLE_HOME)
            if (rt < 0) {
                logger.error "** Failed to deploy ${deployable} on host ${host}"
                return -1
            }


            def dirs = config.flatten().findAll { it -> it.key.toUpperCase().indexOf("DIR") > -1 }.collect {
                it.value
            }.flatten()
            osBuilder.mkdirs(host, dirs)

            logger.info "Deleting consolidated file ......"
            consolidated.getParentFile().deleteDir()

            // specials for zookeeper
            def ug = shell.sshug(host)
            def group = ug.g
            def user = ug.u
            logger.info("** Creating ${config.conf.'zoo.cfg'.dataDir}/myid for {}", host)
            rt = shell.exec("ls -l ${config.conf.'zoo.cfg'.dataDir}/myid", host);
            if (rt.code) {
                rt = shell.exec("echo ${config.settings.hosts.indexOf(host) + 1} > ${config.conf.'zoo.cfg'.dataDir}/myid", host)
                if (!rt.code) {
                    rt = shell.exec("sudo chown ${ug.u}:${ug.g} ${config.conf.'zoo.cfg'.dataDir}/myid", host)
                    rt = shell.exec("stat -c '%n %U %G %y' ${config.conf.'zoo.cfg'.dataDir}/myid", host)
                } else {
                    println "command [echo > myid] runs into error ${rt.msg}"
                }
            } else {
                println "command [ls -l myid] runs into error ${rt.msg}"
            }

        }

    } else {
        logger.error "${host} is not in the server list : ${config.setting.hosts.toString()}"
    }
}

if (!args) {
    logger.info("** Available commands : init, cfg, build(os,optional) mkdir and deploy")
} else {
    if ("init".equalsIgnoreCase(args[0])) {
        new File(CONFIG_FILE_NAME).withWriter { w ->
            w << new File(currentPath, CONFIG_FILE_NAME).text
        }
        logger.info "** Please do the changes according to your environments in ${CONFIG_FILE_NAME}"

    } else {
        def configFile = new File(CONFIG_FILE_NAME)
        if (!configFile.exists()) {
            logger.error "** Can't find file ${CONFIG_FILE_NAME} in current folder ......"
            return -1
        }
        def config = new ConfigSlurper().parse(configFile.text)
        if ("cfg".equalsIgnoreCase(args[0])) {
            cfg(config)
        } else if ("build".equalsIgnoreCase(args[0])) {
            buildOs(config)
        } else if ("mkdir".equalsIgnoreCase(args[0])){
            if(args.length < 2){
                logger.error "please run the command as mkdir host_name"
                return -1
            }
            mkdir(config, args[1])
        } else if ("deploy".equalsIgnoreCase(args[0])) {
            deploy(config, args[1], args[2])
        }
    }
}


