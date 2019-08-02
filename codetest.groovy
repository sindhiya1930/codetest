def getEnvVar(String paramName){
    //get the env from properties file
    return sh (script:"grep '${paramName}' /opt/sample/${TAG}-ms.properties|cut -d'=' -f2", returnStdout: true).trim();
}

pipeline{
    agent any

    stages {
	
			stage('Sence'){
			steps{
			sh '''
			GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
			echo $GIT_COMMIT_HASH
			GIT_TAG=`git describe --tags $(git rev-list --tags --max-count=1)| cut -d'_' -f2`
		case  $GIT_TAG  in
                "consumeraddress")       
 			TAG=$GIT_TAG
                    ;;
		"consumerchild")       
 		TAG=$GIT_TAG
                    ;;
                *)      
		 echo"no tag"
                    ;;
          esac 
			'''
			
			}
			}
        
        stage('Initialization'){
            steps{
               //checkout scm;
                script{
                env.BASE_DIR = pwd()
                env.IMAGE_NAME = getEnvVar('IMAGE_NAME')
                env.JENKINS_GCLOUD_PROJECT_ID = getEnvVar('JENKINS_GCLOUD_PROJECT_ID')
                env.JENKINS_GCLOUD_K8S_CLUSTER_ZONE = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_ZONE')
                env.JENKINS_GCLOUD_K8S_CLUSTER_REGION = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_REGION')
                env.DEPLOY_GCLOUD_PROJECT_ID_DEV= getEnvVar('DEPLOY_GCLOUD_PROJECT_ID_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV')
                env.DEPLOY_GCLOUD_PROJECT_ID_QA = getEnvVar('DEPLOY_GCLOUD_PROJECT_ID_QA')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_NAME_QA  = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_NAME_QA')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_QA  = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_QA')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_REGION_QA  = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_REGION_QA')
                env.DEPLOY_GCLOUD_PROJECT_ID_PREPROD = getEnvVar('DEPLOY_GCLOUD_PROJECT_ID_PREPROD')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_NAME_PREPROD = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_NAME_PREPROD')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_PREPROD = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_PREPROD')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_REGION_PREPROD = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_REGION_PREPROD')
                env.DEPLOYMENT_NAME=getEnvVar('DEPLOYMENT_NAME')
                env.PATH_TO_PARENT_POM=getEnvVar('PATH_TO_PARENT_POM')
                }
            }
        }


        stage('Git Checkout') { // for display purposes 
            steps{
		cleanWs()
             checkout([$class: 'GitSCM', branches: [[name: 'origin/tags/$TAG']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'd5645694-e9d9-4da8-8ef2-dcf70c5e4461', refspec: '+refs/tags/*:refs/remotes/origin/tags/*', url: 'https://github.com/sindhiya1930/codetest.git']]])
            }
       }
	    
	   
	
			stage('Sence'){
			steps{
			sh '''
			echo ${DEPLOYMENT_NAME}
			'''
			
			}
			}
        /*    
    
        stage('PreBuild'){
            steps{
                //Builds the container from Dockerfile
                sh '''
                #description : The script is used to fetch the dependent shared module with respect to the microservice.
                #!/bin/bash
				mkdir /var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms
                #Transfer of microservice and API files to the workspace
                cp -r /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/Microservice/ConsumerChild-ms/* /var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms/
                #Get the list of shared modules currently present
                cd /var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms
                ls | grep 'Mattel.*.parent' > ms_parent.txt
                B="`cat ms_parent.txt`"
                cd /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/SharedModules/
                ls > SM_list.txt
                A="`cat SM_list.txt`"
                shared_module="`echo $A`"
                for i in $shared_module
                    do
                        module=`grep $i /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/Microservice/ConsumerChild-ms/ReadMe.txt | wc -l`
                        if [ $module -eq 1 ]; then
                            echo "Shared module is present in the ReadMe.txt and has to be copied to the workspace"
                            cp -r $i /var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms/
    					    sed -i "s/Mattel.*.parent/`echo $B`/g" /var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms/$i/pom.xml
                        else
                            echo "Shared Module not present in the ReadMe.txt"
                        fi
                    done
                > SM_list.txt
                #rm -rf Parent  
                ''' 
            }   
        }
    stage('Automated Code Review'){
            steps {
                step([$class: 'TibcoBartPipeline', 
    	              bartHome:'/opt/Bart_home',
    	              bartVer:'1.0',
    	              projectName:"Mattel.CM.ConsumerChild.MicroService.application",
    	              projectWorkSpace:"/var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms",
    	              reportDir:"${workspace}"]) 
		   
        	}
        }
    
        stage('Build') {
            steps {
                //build using pom.xml - specify the path of the parent pom
				withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'subram',usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                sh '''
					mvn -f ${PATH_TO_PARENT_POM}/pom.xml clean install
					cd /opt/git/dev/ | mkdir -p ${JOB_NAME}
					cd /opt/git/dev/${JOB_NAME}/
					git init
					git clone -b dev --single-branch https://$USERNAME:$PASSWORD@github.com/mattel-dig/ConsumerMaster--GSL-.git
					cp /var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms/Mattel.CM.ConsumerChild.MicroService.application/target/Mattel.CM.ConsumerChild.MicroService.application_1.0.0.ear /opt/git/dev/${JOB_NAME}/ConsumerMaster--GSL-/deploy_rearch/Earfiles/Microservice/ConsumerChild-ms/Mattel.CM.ConsumerChild.MicroService.application_$(date +%Y%m%d_%H%M%S).ear
					cd /opt/git/dev/${JOB_NAME}/ConsumerMaster--GSL-/deploy_rearch/Earfiles/Microservice/ConsumerChild-ms/
					git add Mattel.CM.ConsumerChild.MicroService.application_$(date +%Y%m%d_%H)*.ear
					git commit -m "$(date +%Y%m%d_%H%M)"
					cp /var/lib/jenkins/workspace/${JOB_NAME}/*.html /opt/git/dev/${JOB_NAME}/ConsumerMaster--GSL-/deploy_rearch/Bart_report/Microservice/ConsumerChild-ms/ConsumerChild-ms_report_$(date +%Y%m%d_%H%M%S).html
					cd /opt/git/dev/${JOB_NAME}/ConsumerMaster--GSL-/deploy_rearch/Bart_report/Microservice/ConsumerChild-ms/
					git add ConsumerChild-ms_report_$(date +%Y%m%d_%H%M)*.html
					git commit -m "$(date +%Y%m%d_%H%M)"
					git push https://$USERNAME:$PASSWORD@github.com/mattel-dig/ConsumerMaster--GSL-/ dev
					rm -rf /opt/git/dev/${JOB_NAME}/*
		
				'''
				cleanWs()
		        }
            }
        }
         stage('Docker Containerisation'){
            steps{
                //Builds the container from Dockerfile
                sh '''
                #This gets the Git commit id 
                GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
                echo $GIT_COMMIT_HASH
                
                #Copies the Dockerfile to the path where .ear file is created
                cd /var/lib/jenkins/workspace/${JOB_NAME}/ConsumerChild-ms/Mattel.CM.ConsumerChild.MicroService.application/target/
                pwd
                cp /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/dockerfiles/Microservice/ConsumerChild-ms/Dockerfile Dockerfile
                pwd
                #Builds the images with git commitid and latest tag
                docker build -t gcr.io/${JENKINS_GCLOUD_PROJECT_ID}/${IMAGR_NAME}:$GIT_COMMIT_HASH .
                ''' 
            }   
        }
        
            
        stage('Image Publish to GCR'){
            steps{
                sh '''
                #This gets the Git commit id 
                GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
                echo $GIT_COMMIT_HASH
                gcloud config set compute/zone ${JENKINS_GCLOUD_K8S_CLUSTER_ZONE}       
                gcloud config set compute/region ${JENKINS_GCLOUD_K8S_CLUSTER_REGION}
                gcloud config set project ${JENKINS_GCLOUD_PROJECT_ID}
                gcloud auth configure-docker
                #Pushes Docker images into GCR
                docker push gcr.io/${JENKINS_GCLOUD_PROJECT_ID}/${IMAGR_NAME}:$GIT_COMMIT_HASH
                ''' 
            }
        }
    stage('Deployment to DEV GKE'){
            steps{
                withCredentials([file(credentialsId: 'mattelCreds', variable: 'mattel')]) {
                    sh '''
                    #Sets the env for gcloud
                    gcloud auth activate-service-account --key-file=${mattel}
                    gcloud config set compute/zone ${DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV}
                    gcloud config set compute/region ${DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV}
                    gcloud config set project ${DEPLOY_GCLOUD_PROJECT_ID_DEV}
                    #Though --zone is mentioned for get-credentials ,provide the region
                    gcloud container clusters get-credentials ${DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV} --zone ${DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV} 
                    GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
					echo $GIT_COMMIT_HASH
                    sed -i s/latest/`echo $GIT_COMMIT_HASH`/g /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/DEV/manifest-cm-${TAG}-ms_dev.yml
                    #Checks for any deployment
                    #kubectl get deployments ${DEPLOYMENT_NAME}
                    #RESULT=$?
                    #echo $RESULT
                    #if [ $RESULT -eq 1 ]; then
                    echo "Deployment already exists! so updating the deployment"
                    kubectl apply -f /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/DEV/manifest-cm-${TAG}-ms_dev.yml
                    kubectl rollout status deployment ${DEPLOYMENT_NAME}
                    #else
                    #echo "Creating a new deployment"
                    #kubectl create -f /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/DEV/manifest-cm-${TAG}-ms_dev.yml
                    #fi
                    ''' 
                }
            }
        }  
        
stage('DEV-SanityTesting&checkforRollback'){
            steps{
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'devApiCreds',usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    sh '''
                    sleep 30
                    #post deployment verification and rollback
                    #post deployment script for dev_deploymentaddress.yml file
                    curl -X GET --header 'Accept: application/json' --header 'ConsumerID: 10001' 'http://34.66.167.240:80/${TAG}' -u $USERNAME:$PASSWORD > result.txt
                    status=`grep -E "Bad Request|Server Error" result.txt| wc -l`
                    echo $status
                    if [ $status -eq 0 ]; then
                        echo "Deployment has been rolled out successfully"
                        GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
                        echo $GIT_COMMIT_HASH
                        echo $GIT_COMMIT_HASH >> /opt/docker_tag/phase1b_tag/dev_docker_tag/Microservice/${TAG}-ms_tag.txt
                    else
                        echo "Deployment wasn't successfull, rolling back the deploy to the previous successfull image"
                        image=`tail -n 1 /opt/docker_tag/phase1b_tag/dev_docker_tag/Microservice/${TAG}-ms_tag.txt`
                        echo "Last successfull $image image is going to be deployed"
                        kubectl apply -f /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/DEV/manifest-cm-${TAG}-ms_dev.yml
                        kubectl rollout status deployment ${DEPLOYMENT_NAME}
                    fi
                    rm -rf result.txt
                    ''' 
                }
            }
        } 
	    
	stage('Approve deploy to QA (Wait 25sec)') { // for display purposes 
            steps{
                timeout(time: 25, unit: 'SECONDS'){
                input 'Do you want to proceed for QA deployment'
            }
                echo "Approval for QA Deployment"
                
            }
       }
	    
	
	stage('Deployment to QA GKE') { // for display purposes 
            steps{
                /// deployment to  Pre-Prod GKE 
                withCredentials([file(credentialsId: 'mattelCreds', variable: 'mattel')]) {
                    sh '''
                    #Sets the env for gcloud
                    gcloud auth activate-service-account --key-file=${mattel}
                    gcloud config set compute/zone ${DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_QA}
                    gcloud config set compute/region ${DEPLOY_GCLOUD_K8S_CLUSTER_REGION_QA}
                    gcloud config set project ${DEPLOY_GCLOUD_PROJECT_ID_QA}
                    #Though --zone is mentioned for get-credentials ,provide the region
                    gcloud container clusters get-credentials ${DEPLOY_GCLOUD_K8S_CLUSTER_NAME_QA} --zone ${DEPLOY_GCLOUD_K8S_CLUSTER_REGION_QA} 
                    GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
					echo $GIT_COMMIT_HASH
                    sed -i s/latest/`echo $GIT_COMMIT_HASH`/g /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/QA/manifest-cm-${TAG}-ms_qa.yml
                    #Checks for any deployment
                    #kubectl get deployments ${DEPLOYMENT_NAME}
                    #RESULT=$?
                    #echo $RESULT
                    #if [ $RESULT -eq 1 ]; then
                    echo "Deployment already exists! so updating the deployment"
                    kubectl apply -f /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/QA/manifest-cm-${TAG}-ms_qa.yml
                    kubectl rollout status deployment ${DEPLOYMENT_NAME}
                    #else
                    #echo "Creating a new deployment"
                    #kubectl create -f /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/QA/manifest-cm-${TAG}-ms_qa.yml
                    #fi
                    ''' 
                }  
            }
       }
        stage('QA-SanityTesting&CheckforRollback') { // for display purposes 
            steps{
            withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'qaApiCreds',usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                // deployment to  Pre-Prod GKE 
                sh '''
                    sleep 30
                    #post deployment verification and rollback
                    #post deployment script for dev_deploymentaddress.yml file
                    curl -X GET --header 'Accept: application/json' --header 'ConsumerID: 100001' 'http://34.67.200.241:80/${TAG}' -u $USERNAME:$PASSWORD > result.txt
                    status=`grep -E "Bad Request|Server Error" result.txt| wc -l`
                    echo $status
                    if [ $status -eq 0 ]; then
                        echo "Deployment has been rolled out successfully"
                        GIT_COMMIT_HASH=`git log -n 1 --pretty=format:%H`
                        echo $GIT_COMMIT_HASH
                        echo $GIT_COMMIT_HASH >> opt/docker_tag/phase1b_tag/qa_docker_tag/Microservice/qa-${TAG}-ms_tag.txt
                    else
                        echo "Deployment wasn't successfull, rolling back the deploy to the previous successfull image"
                        image=`tail -n 1 /opt/docker_tag/phase1b_tag/qa_docker_tag/Microservice/qa-${TAG}-ms_tag.txt`
                        echo "Last successfull $image image is going to be deployed"
                        kubectl apply -f /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/manifest.yml/QA/manifest-cm-${TAG}-ms_qa.yml
                        kubectl rollout status deployment ${DEPLOYMENT_NAME}
                    fi
                    rm -rf result.txt
                    '''
                }
            }
        } */

}
}
