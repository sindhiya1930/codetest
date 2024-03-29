def getEnvVar(String paramName){
    //get the env from properties file
	return sh (script:"grep '${paramName}' /opt/properties/phase1b_properties/dev_properties/${SERVICE_NAME}.properties|cut -d'=' -f2", returnStdout: true).trim();
}


pipeline{
    agent any
           environment {
               SERVICE_NAME = sh(script: "git describe --tags \$(git rev-list --tags --max-count=1)| cut -d'_' -f1", , returnStdout: true).trim()
          }
   
	stages {
		
	stage('Initialization'){
            steps{
                //checkout scm
                script{
		env.CATEGORY = getEnvVar('CATEGORY')
                env.JENKINS_GCLOUD_PROJECT_ID = getEnvVar('JENKINS_GCLOUD_PROJECT_ID')
                env.JENKINS_GCLOUD_K8S_CLUSTER_ZONE = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_ZONE')
                env.JENKINS_GCLOUD_K8S_CLUSTER_REGION = getEnvVar('JENKINS_GCLOUD_K8S_CLUSTER_REGION')
                env.DEPLOY_GCLOUD_PROJECT_ID_DEV= getEnvVar('DEPLOY_GCLOUD_PROJECT_ID_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_NAME_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_ZONE_DEV')
                env.DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV = getEnvVar('DEPLOY_GCLOUD_K8S_CLUSTER_REGION_DEV')
		env.PARAMETERS = getEnvVar('PARAMETERS')
		env.URL = getEnvVar('URL')
		env.PROJECT_NAME = getEnvVar('PROJECT_NAME')	
                }
            }
        }
		
 stage('Git Checkout') { // for display purposes 
            steps{
                cleanWs()
		checkout([$class: 'GitSCM', branches: [[name: 'refs/tags/*_dev_*']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'git-service-acc', refspec: '+refs/tags/*:refs/remotes/origin/tags/*', url: 'https://github.com/mattel-dig/ConsumerMaster--GSL-.git']]])
	    }
	}
   
        stage('PreBuild'){
            steps{
                //Builds the container from Dockerfile
                sh '''
                #description : The script is used to fetch the dependent shared module with respect to the API.
                #!/bin/bash
		echo ${SERVICE_NAME}
		 mkdir /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
		 chmod -R 777 /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
                #Transfer of API and API files to the workspace
                cp -r /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/${CATEGORY}/$SERVICE_NAME/* /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME/
                #Get the list of shared modules currently present
                cd /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
                ls | grep 'Mattel.*.parent' > ms_parent.txt
                B="`cat ms_parent.txt`"
                cd /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/SharedModules/
                ls > SM_list.txt
                A="`cat SM_list.txt`"
                shared_module="`echo $A`"
                for i in $shared_module
                    do
                        module=`grep $i /var/lib/jenkins/workspace/${JOB_NAME}/code_rearch/${CATEGORY}/${SERVICE_NAME}/ReadMe.txt | wc -l`
                        if [ $module -eq 1 ]; then
                            echo "Shared module is present in the ReadMe.txt and has to be copied to the workspace"
                            cp -r $i /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME/
                            sed -i "s/Mattel.*.parent/`echo $B`/g" /var/lib/jenkins/workspace/${JOB_NAME}/${SERVICE_NAME}/$i/pom.xml
                        else
                            echo "Shared Module not present in the ReadMe.txt"
                        fi
                    done
                > SM_list.txt
		cd /var/lib/jenkins/workspace/${JOB_NAME}/$SERVICE_NAME
                	ls | grep 'Mattel.*.application' > ms_application.txt
                	App_folder="`head -1 ms_application.txt`"
                ''' 
            }   
        }
 
		 stage('Automated Code Review'){
            steps {
                step([$class: 'TibcoBartPipeline', 
    	              bartHome:'/opt/Bart_home',
    	              bartVer:'1.0',
    	              projectName:"${PROJECT_NAME}",
    	              projectWorkSpace:"/var/lib/jenkins/workspace/${JOB_NAME}/${SERVICE_NAME}",
    	              reportDir:"${workspace}"])
		  }
        	}

   
         stage('Build') {
            steps {
                //build using pom.xml - specify the path of the parent pom
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'subram',usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                sh '''
                mvn -f ${SERVICE_NAME}/${PROJECT_NAME}.parent/pom.xml clean install

		
		'''
                }
            }
        }
		
		stage('Docker Containerisation'){
            steps{
                //Builds the container from Dockerfile
                sh '''
                cd /var/lib/jenkins/workspace/${JOB_NAME}/${SERVICE_NAME}/${PROJECT_NAME}/target/
                cp /var/lib/jenkins/workspace/${JOB_NAME}/deploy_rearch/dockerfiles/${CATEGORY}/${SERVICE_NAME}/Dockerfile Dockerfile
                docker build -t gcr.io/${JENKINS_GCLOUD_PROJECT_ID}/cm-${SERVICE_NAME}-devops:$GIT_COMMIT .
                ''' 
            }   
        }
          
        stage('Image Publish to GCR'){
            steps{
                sh '''
                #This gets the Git commit id 
                gcloud config set compute/zone ${JENKINS_GCLOUD_K8S_CLUSTER_ZONE}       
                gcloud config set compute/region ${JENKINS_GCLOUD_K8S_CLUSTER_REGION}
                gcloud config set project ${JENKINS_GCLOUD_PROJECT_ID}
                gcloud auth configure-docker
                #Pushes Docker images into GCR
                docker push gcr.io/${JENKINS_GCLOUD_PROJECT_ID}/cm-${SERVICE_NAME}-devops:$GIT_COMMIT
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
                    sed -i s/latest/`echo $GIT_COMMIT`/g /opt/deploymentfiles/phase1b_deployment/dev_deployment/${SERVICE_NAME}_dev.yml
                    #Checks for any deployment
                    #kubectl get deployments cm-${SERVICE_NAME}-devops
                    #RESULT=$?
                    #echo $RESULT
                    #if [ $RESULT -eq 1 ]; then
                    echo "Deployment already exists! so updating the deployment"
                    kubectl apply -f /opt/deploymentfiles/phase1b_deployment/dev_deployment/${SERVICE_NAME}_dev.yml
                    kubectl rollout status deployment cm-${SERVICE_NAME}-devops
                    #else
                    #echo "Creating a new deployment"
                    #kubectl create -f /opt/deploymentfiles/phase1b_deployment/dev_deployment/${SERVICE_NAME}_dev.yml
                    #fi
                    ''' 
                }
            }
        } 
		
        stage('DEV-SanityTesting&checkforRollback'){
            steps{
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'devApiCreds',usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {
                    sh '''
                    sleep 140
                    #post deployment verification and rollback
                    #post deployment script for dev_deploymentaddress.yml file
                    curl -X GET --header 'Accept: application/json' --header ${PARAMETERS} ${URL} -u $USERNAME:$PASSWORD > result.txt
                    status=`grep -E "Bad Request|Server Error" result.txt| wc -l`
                    echo $status
                    if [ $status -eq 0 ]; then
                        echo "Deployment has been rolled out successfully"
                        echo $GIT_COMMIT
                        echo $GIT_COMMIT >> /opt/docker_tag/phase1b_tag/dev_docker_tag/${CATEGORY}/${SERVICE_NAME}_tag.txt
                    else
                        echo "Deployment wasn't successfull, rolling back the deploy to the previous successfull image"
                        image=`tail -n 1 /opt/docker_tag/phase1b_tag/dev_docker_tag/${CATEGORY}/${SERVICE_NAME}_tag.txt`
                        echo "Last successfull $image image is going to be deployed"
                        kubectl apply -f /opt/deploymentfiles/phase1b_deployment/dev_deployment/${SERVICE_NAME}_dev.yml
                        kubectl rollout status deployment cm-${SERVICE_NAME}-devops
                    fi
                    rm -rf result.txt
                    ''' 
                }
            }
        } 
}
}
		
