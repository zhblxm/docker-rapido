package com.github.howaric.docker_rapido.core;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.howaric.docker_rapido.core.RapidoDockerRunner.RapidoDockerRunnerFactory;
import com.github.howaric.docker_rapido.docker.DockerProxy;
import com.github.howaric.docker_rapido.docker.DockerProxyFactory;
import com.github.howaric.docker_rapido.yaml_model.Node;
import com.github.howaric.docker_rapido.yaml_model.RapidoTemplate;
import com.github.howaric.docker_rapido.yaml_model.Service;

public class ServiceTaskHandler {

    private static Logger logger = LoggerFactory.getLogger(ServiceTaskHandler.class);

    private RapidoTemplate rapidoTemplate;
    private String serviceName;
    private List<Node> targetNodes;
    private String imageTag;

    public ServiceTaskHandler(RapidoTemplate rapidoTemplate, String serviceName, List<Node> targetNodes, String imageTag) {
        super();
        this.rapidoTemplate = rapidoTemplate;
        this.serviceName = serviceName;
        this.targetNodes = targetNodes;
        this.imageTag = imageTag;
    }

    public void runTask() {
        logger.info("Get job");
        // build image if need
        Service service = rapidoTemplate.getServices().get(serviceName);
        String image = service.getImage();
        String imageNameWithRepoAndTag = rapidoTemplate.getRepo() + "/" + image;
        if (imageTag != null) {
            DockerProxy optDocker = DockerProxyFactory.getInstance(rapidoTemplate.getRemote_docker());
            imageNameWithRepoAndTag = imageNameWithRepoAndTag + ":" + imageTag;
            String imageId = optDocker.buildImage(service.getBuild(), imageNameWithRepoAndTag);
            logger.info("Build successfully, imageId is {}", imageId);
        }

        // go to each node and do operation
        DeployPolicy deployPolicy = service.getDeploy().getDeployPolicy();

        // rolling-update 起新的，删除之前的。。。
        // on-absent 存在跳过，不存在启动
        for (Node node : targetNodes) {
            RapidoDockerRunner runner = RapidoDockerRunnerFactory.getInstance(deployPolicy);
            runner.start(rapidoTemplate.getDeliver_type(), rapidoTemplate.getOwner(), node, serviceName, service, imageNameWithRepoAndTag);
        }

    }

}
