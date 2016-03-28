# -*- coding: utf-8 -*-
"""
Created on Fri Mar 25 18:00:02 2016

@author: minxu

This script is used to automatically conduct experiments on performance
of query expansion with different parameters. For each experiment, 
it will generate parameter file based on experiment requirements, 
call the complied Java class QryEval which generates result file,
upload the result file through the course online test server through
http post requests and multipart form, and finally parse the responded
html text to get MAP and P@N values and win loss ratio.

To use this script, please put this in the same folder of the QryEval.class
and other java class files, and correspondingly change the path parameters

"""

import subprocess
import urllib2, base64 
import poster
from poster.encode import multipart_encode, MultipartParam

#==============================================================================
# global variables
#==============================================================================
wd = "C:\\Users\\Administrator\\Desktop\\hw\\"
command = "java -version:1.8 -Xmx4g -cp \"C:\\Users\\Administrator\\Desktop\\hw\\hw1\\lucene-4.3.0\\*;.\" QryEval "
parameters = dict()
parameters["indexPath"]="C:\\Users\\Administrator\\Desktop\\hw\\hw1\\index-1.1\\"
queryFilePathFolder="C:\\Users\\Administrator\\Desktop\\hw\\hw4\\"
trecEvalOutputPathFolder="C:\\Users\\Administrator\\Desktop\\hw\\hw4\\output1\\"
filesFolder = "C:\\Users\\Administrator\\Desktop\\hw\\hw4\\parameterFilesFolder\\"
fbExpansionQueryFileFolder="C:\\Users\\Administrator\\Desktop\\hw\\hw4\\output2\\"
fbInitialRankingFile = "C:\\Users\\Administrator\\Desktop\\hw\\hw4\\Indri-Bow.teIn"
queryFile = "queries.txt"
url = 'http://boston.lti.cs.cmu.edu/classes/11-642/HW/HTS/tes.cgi'
username = 'minxu'
password = 'xmanswer'
opener = poster.streaminghttp.register_openers()
urllib2.install_opener(opener)

__doMeasurement__ = False #switch for measurement

#==============================================================================
# functions def
#==============================================================================
#==============================================================================
# write new experiment parameters to local file
#==============================================================================
def writeToParameterFile(parameterFileName, parameters):
    with open(filesFolder + parameterFileName, 'w') as f:
        f.write("indexPath=" + parameters["indexPath"] + "\n")
        f.write("retrievalAlgorithm=" + parameters["retrievalAlgorithm"] + "\n")
        f.write("queryFilePath=" + parameters["queryFilePath"] + "\n")        
        f.write("trecEvalOutputPath=" + parameters["trecEvalOutputPath"] + "\n")
        if parameters["retrievalAlgorithm"] == "Indri":     
            f.write("Indri:mu=" + str(parameters["Indri_mu"]) + "\n")
            f.write("Indri:lambda=" + str(parameters["Indri_lambda"]) + "\n")
            if parameters["fb"] == "true":
                f.write("fb=" + parameters["fb"] + "\n")
                f.write("fbDocs=" + str(parameters["fbDocs"]) + "\n")
                f.write("fbTerms=" + str(parameters["fbTerms"]) + "\n")
                f.write("fbMu=" + str(parameters["fbMu"]) + "\n")
                f.write("fbOrigWeight=" + str(parameters["fbOrigWeight"]) + "\n")
                f.write("fbExpansionQueryFile=" + fbExpansionQueryFileFolder + parameterFileName + "\n")
                if "fbInitialRankingFile" in parameters:
                    f.write("fbInitialRankingFile=" + parameters["fbInitialRankingFile"] + "\n")
                    
#==============================================================================
# conduct experiemnt by calling java QryEval process
#==============================================================================
def runExperiment(parameterFileName):
    arg = command + filesFolder + parameterFileName
    p = subprocess.Popen(arg, stdout=subprocess.PIPE, stderr=subprocess.STDOUT) 
    for i in p.stdout: 
        print i

#==============================================================================
# submit the outputfile to test server and get response text
#==============================================================================
def submit(outputfile):
    items = []
    items.append(MultipartParam('logtype', 'Detailed'))
    items.append(MultipartParam('hwid', 'HW4'))
    items.append(MultipartParam.from_file('infile', trecEvalOutputPathFolder + outputfile))
    datagen, headers = multipart_encode(items)
    base64string = base64.encodestring('%s:%s' % (username, password)).replace('\n', '')
    headers['Authorization'] = "Basic %s" % base64string
    request = urllib2.Request(url, datagen, headers)
    result = urllib2.urlopen(request)
    t = result.read()
    map_all, P10, P20, P30, win, loss, mapdict = findMAP(t)
    return map_all, P10, P20, P30, win, loss

#==============================================================================
# parse the response text and get useful performance metric
#==============================================================================
def findMAP(t):
    baseline = 0.0750
    start = -1
    mapdict = dict()
    win = 0
    loss = 0
    for i in range(0, 20):
        mapindex = t.find('map', start+1)
        reststr = t[mapindex : -1]
        value = reststr[reststr.find('<br>')-6 : reststr.find('<br>')]
        queryidIndex = reststr.find('\t') + 1
        queryid = int(reststr[queryidIndex : queryidIndex + 3])
        mapdict[queryid] = value
        start = mapindex + 1
        if value > baseline:
            win = win + 1
        else:
            loss = loss + 1
    mapindex = t.find('map', start+1)
    reststr = t[mapindex : -1]
    map_all = reststr[reststr.find('<br>')-6 : reststr.find('<br>')]
    reststr = reststr[reststr.find('P10') : -1]
    P10 = reststr[reststr.find('<br>')-6 : reststr.find('<br>')]
    reststr = reststr[reststr.find('P20') : -1]
    P20 = reststr[reststr.find('<br>')-6 : reststr.find('<br>')]
    reststr = reststr[reststr.find('P30') : -1]
    P30 = reststr[reststr.find('<br>')-6 : reststr.find('<br>')]
    return map_all, P10, P20, P30, win, loss, mapdict

if __doMeasurement__:
    #==============================================================================
    # experiment 1: baselines
    #==============================================================================
    exp1_result = []
    
    #RankedBoolean results
    print "********************************************************************"
    print "do exp1_1"
    parameterFileName = "exp1_1"
    parameters["retrievalAlgorithm"]="RankedBoolean"
    parameters["queryFilePath"]=queryFilePathFolder + queryFile
    parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
    #writeToParameterFile(parameterFileName, parameters)
    #runExperiment(parameterFileName)
    res = submit(parameterFileName)
    print res
    exp1_result.append(res)
    
    #Indri results
    print "********************************************************************"
    print "do exp1_2"
    parameters["retrievalAlgorithm"]="Indri"
    parameters["Indri_mu"]=1000
    parameters["Indri_lambda"]=0.7
    parameters["fb"]="false"
    parameterFileName = "exp1_2"
    parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
    writeToParameterFile(parameterFileName, parameters)
    runExperiment(parameterFileName)
    res = submit(parameterFileName)
    print res
    exp1_result.append(res)
    
    #Indri results reference Indri-Bow
    print "********************************************************************"
    print "do exp1_3_1"
    parameterFileName = "Indri-Bow.teIn"
    res = submit(parameterFileName)
    print res
    exp1_result.append(res)
    
    #Indri results reference Indri-Sdm
    print "********************************************************************"
    print "do exp1_3_2"
    parameterFileName = "Indri-Sdm.teIn"
    res = submit(parameterFileName)
    print res
    exp1_result.append(res)
    
    #Indri results using query expansion on my system retrieve results
    print "********************************************************************"
    print "do exp1_4"
    parameters["fb"]="true"
    parameters["fbDocs"]=10
    parameters["fbTerms"]=10
    parameters["fbMu"]=0
    parameters["fbOrigWeight"]=0.5
    parameterFileName = "exp1_4"
    parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
    writeToParameterFile(parameterFileName, parameters)
    runExperiment(parameterFileName)
    res = submit(parameterFileName)
    print res
    exp1_result.append(res)
    
    #Indri results using query expansion on reference system results
    print "********************************************************************"
    print "do exp1_5"
    parameterFileName = "exp1_5"
    parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
    parameters["fbInitialRankingFile"] = fbInitialRankingFile
    writeToParameterFile(parameterFileName, parameters)
    runExperiment(parameterFileName)
    res = submit(parameterFileName)
    print res
    exp1_result.append(res)
    
    #==============================================================================
    # experiment 2: change num of feedback document number
    #==============================================================================
    fbDocNumList = [10, 20, 30, 40, 50, 100]
    exp2_result = []
    map_exp2_all= []
    
    for i in range(1, 7):
        print "********************************************************************"
        print "do exp2_" + str(i)
        fbDocNum = fbDocNumList[i-1]
        parameters["fb"]="true"
        parameters["fbDocs"]=fbDocNum
        parameters["fbTerms"]=10
        parameters["fbMu"]=0
        parameters["fbOrigWeight"]=0.5
        parameterFileName = "exp2_" + str(i)
        parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
        parameters["fbInitialRankingFile"] = fbInitialRankingFile
        writeToParameterFile(parameterFileName, parameters)
        runExperiment(parameterFileName)
        res = submit(parameterFileName)
        print res
        exp2_result.append(res)
        map_exp2_all.append(float(res[0]))
    
    #find the best fbDocs
    m = max(map_exp2_all)
    bestid = [i for i, j in enumerate(map_exp2_all) if j == m][0]
    bestFbDocs = fbDocNumList[bestid]
    
    #==============================================================================
    # experiment 3: change num of feedback term number
    #==============================================================================
    fbTermNumList = [5, 10, 20, 30, 40, 50]
    exp3_result = []
    map_exp3_all = []
    
    for i in range(1, 2):
        print "********************************************************************"
        print "do exp3_" + str(i)
        fbTerms = fbTermNumList[i-1]
        parameters["fb"]="true"
        parameters["fbDocs"]=bestFbDocs
        parameters["fbTerms"]=fbTerms
        parameters["fbMu"]=0
        parameters["fbOrigWeight"]=0.5
        parameterFileName = "exp3_" + str(i)
        parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
        parameters["fbInitialRankingFile"] = fbInitialRankingFile
        writeToParameterFile(parameterFileName, parameters)
        runExperiment(parameterFileName)
        res = submit(parameterFileName)
        print res
        exp3_result.append(res)
        map_exp3_all.append(float(res[0]))
    
    #find the best fbTerms
    m = max(map_exp3_all)
    bestid = [i for i, j in enumerate(map_exp3_all) if j == m][0]
    bestFbTerms = fbTermNumList[bestid]
    
    #==============================================================================
    # experiment 4: change fbOrigWeight 
    #==============================================================================
    fbOrigWeightList = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
    exp4_result = []
    map_exp4_all = []
    for i in range(1, 7):
        print "********************************************************************"
        print "do exp4_" + str(i)
        fbOrigWeight = fbOrigWeightList[i-1]
        parameters["fb"]="true"
        parameters["fbDocs"]=bestFbDocs
        parameters["fbTerms"]=bestFbTerms
        parameters["fbMu"]=0
        parameters["fbOrigWeight"]=fbOrigWeight
        parameterFileName = "exp4_" + str(i)
        parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
        parameters["fbInitialRankingFile"] = fbInitialRankingFile
        writeToParameterFile(parameterFileName, parameters)
        runExperiment(parameterFileName)
        res = submit(parameterFileName)
        print res
        exp4_result.append(res)
        map_exp4_all.append(float(res[0]))
    
    #find the best fbOrigWeight
    m = max(map_exp4_all)
    bestid = [i for i, j in enumerate(map_exp4_all) if j == m][0]
    bestFbOrigWeight = fbOrigWeightList[bestid]
    
    #==============================================================================
    # experiment 5:
    #==============================================================================
    print "********************************************************************"
    print "do exp5_1"
    parameters["fb"]="true"
    parameters["fbDocs"]=100
    parameters["fbTerms"]=50
    parameters["fbMu"]=0
    parameters["fbOrigWeight"]=1.0
    parameterFileName = "exp5_1"
    parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
    parameters["fbInitialRankingFile"] = "C:\\Users\\Administrator\\Desktop\\hw\\hw4\\Indri-Sdm.teIn"
    writeToParameterFile(parameterFileName, parameters)
    runExperiment(parameterFileName)
    res = submit(parameterFileName)
    print res
    
    parameters["queryFilePath"]=queryFilePathFolder + "Indri-Sdm.qry"
    
    print "********************************************************************"
    print "do exp5_2"
    parameters["fb"]="true"
    parameters["fbDocs"]=100
    parameters["fbTerms"]=50
    parameters["fbMu"]=0
    parameters["fbOrigWeight"]=1.0
    parameterFileName = "exp5_2"
    parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
    parameters["fbInitialRankingFile"] = "C:\\Users\\Administrator\\Desktop\\hw\\hw4\\Indri-Bow.teIn"
    writeToParameterFile(parameterFileName, parameters)
    runExperiment(parameterFileName)
    res = submit(parameterFileName)
    print res
    
    print "********************************************************************"
    print "do exp5_3"
    parameters["fb"]="true"
    parameters["fbDocs"]=100
    parameters["fbTerms"]=50
    parameters["fbMu"]=0
    parameters["fbOrigWeight"]=1.0
    parameterFileName = "exp5_3"
    parameters["trecEvalOutputPath"]=trecEvalOutputPathFolder + parameterFileName
    parameters["fbInitialRankingFile"] = "C:\\Users\\Administrator\\Desktop\\hw\\hw4\\Indri-Sdm.teIn"
    writeToParameterFile(parameterFileName, parameters)
    runExperiment(parameterFileName)
    res = submit(parameterFileName)
    print res

#==============================================================================
# final evaluation, generate csv files for all measured performance metrics
#==============================================================================
with open('evaluation.csv', 'w') as f:
    f.write('map_all,P10,P20,P30,win,loss\n')
    #exp1
    print "evaluate exp1"
    f.write('exp1\n')
    exp1list = ['1', '2', '3_1', '3_2', '4', '5']
    for exp in exp1list:
        filename = 'exp1_' + exp
        res = submit(filename)
        f.write(','.join(map(str, res)) + '\n')
    #exp 2-4
    for i in range(2, 5):
        f.write('exp' + str(i) + '\n')
        for j in range(1, 7):
            filename = 'exp' + str(i) + '_' + str(j)
            print "evaluate " + filename
            res = submit(filename)
            f.write(','.join(map(str, res)) + '\n')
    
    #exp 5
    f.write('exp5' + '\n')
    for i in range(1, 4):
        filename = 'exp5_' + str(i)
        print "evaluate " + filename
        res = submit(filename)
        f.write(','.join(map(str, res)) + '\n')       