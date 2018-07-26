package com.github.howaric.docker_rapido.docker;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import com.github.howaric.docker_rapido.utils.CommonUtil;

public class DefaultDockerProxy implements DockerProxy {

	private static Logger logger = LoggerFactory.getLogger(DefaultDockerProxy.class);

	private DockerClient dockerClient;

	DefaultDockerProxy() {
		super();
	}

	DefaultDockerProxy(String endPoint) {
		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(endPoint).build();
		dockerClient = DockerClientBuilder.getInstance(config).build();
	}

	@Override
	public String buildImage(String localDockerfilePath, String... imageTag) {
		File file = new File(localDockerfilePath);
		BuildImageResultCallback buildImageResultCallback = new BuildImageResultCallback() {
			@Override
			public void onNext(BuildResponseItem item) {
				super.onNext(item);
			}
		};
		Set<String> tags = new HashSet<>();
		for (int i = 0; i < imageTag.length; i++) {
			tags.add(imageTag[i]);
		}
		String imageId = dockerClient.buildImageCmd(file).withTags(tags).exec(buildImageResultCallback).awaitImageId();
		return imageId;
	}

	@Override
	public void pushImage(String imageNameWithRepoAndTag, String username, String password) {
		PushImageResultCallback pushImageResultCallback = new PushImageResultCallback() {
			@Override
			public void onNext(PushResponseItem item) {
				super.onNext(item);
			}
		};
		if (username != null && password != null) {
			AuthConfig authConfig = dockerClient.authConfig().withUsername(username).withPassword(password);
			dockerClient.pushImageCmd(imageNameWithRepoAndTag).withAuthConfig(authConfig).exec(pushImageResultCallback).awaitSuccess();
		} else {
			dockerClient.pushImageCmd(imageNameWithRepoAndTag).exec(pushImageResultCallback).awaitSuccess();
		}
	}

	@Override
	public void removeImage(String imageId) {
		dockerClient.removeImageCmd(imageId).exec();
		logger.info("Image {} Removing successfully", imageId);
	}

	@Override
	public void pullImage(String imageNameWithTag, String username, String password) {
		PullImageResultCallback pullImageResultCallback = new PullImageResultCallback();
		if (!Strings.isNullOrEmpty(username) && !Strings.isNullOrEmpty(password)) {
			AuthConfig authConfig = dockerClient.authConfig().withUsername(username).withPassword(password);
			dockerClient.pullImageCmd(imageNameWithTag).withAuthConfig(authConfig).exec(pullImageResultCallback).awaitSuccess();
		} else {
			dockerClient.pullImageCmd(imageNameWithTag).exec(pullImageResultCallback).awaitSuccess();
		}
	}

	@Override
	public String isImageExited(String imageNameWithTag) {
		List<Image> imageList = dockerClient.listImagesCmd().exec();
		for (Image image : imageList) {
			String[] repoTags = image.getRepoTags();
			if (repoTags != null) {
				for (String repoTag : repoTags) {
					if (imageNameWithTag.equals(repoTag)) {
						return image.getId();
					}
				}
			}
		}
		return null;
	}

	@Override
	public List<Container> listContainers(boolean isShowAll) {
		List<Container> containerList = dockerClient.listContainersCmd().withShowAll(isShowAll).exec();
		return containerList;
	}

	@Override
	public void stopContainer(String containerId, Integer timeout) {
		try {
			StopContainerCmd stopContainerCmd = dockerClient.stopContainerCmd(containerId);
			if (timeout != null && timeout > 0) {
				stopContainerCmd = stopContainerCmd.withTimeout(timeout);
			}
			stopContainerCmd.exec();
		} catch (NotModifiedException e) {
			logger.error("No such Container " + containerId);
		}
	}

	@Override
	public void startContainer(String containerId) {
		try {
			dockerClient.startContainerCmd(containerId).exec();
		} catch (NotModifiedException e) {
			logger.error("No such Container " + containerId);
		}
	}

	@Override
	public void restartContainer(String containerId) {
		try {
			dockerClient.restartContainerCmd(containerId).exec();
		} catch (NotModifiedException e) {
			logger.error("No such Container " + containerId);
		}
	}

	@Override
	public String createContainer(String name, String imageNameWithTag, List<String> ports, List<String> envs, List<String> links,
			List<String> volumes, List<String> extraHosts) {
		logger.info("Start to created container: {}", name);
		CreateContainerCmd cmd = dockerClient.createContainerCmd(imageNameWithTag).withName(name);

		if (CommonUtil.hasElement(ports)) {
			List<ExposedPort> exposePortList = new ArrayList<>();
			Ports portBindings = new Ports();
			for (String port : ports) {
				if (port.contains(":")) {
					String[] split = port.split(":");
					String hostPort = split[0];
					String containerExposedPort = split[1];
					ExposedPort expose = ExposedPort.tcp(Integer.parseInt(containerExposedPort));
					portBindings.bind(expose, Ports.Binding.bindPort(Integer.parseInt(hostPort)));
					exposePortList.add(expose);
				} else {
					ExposedPort expose = ExposedPort.tcp(Integer.parseInt(port));
					exposePortList.add(expose);
					// cmd = cmd.withPublishAllPorts(true);
				}
			}
			if (!exposePortList.isEmpty()) {
				cmd = cmd.withExposedPorts(exposePortList);
			}
			if (!portBindings.getBindings().isEmpty()) {
				cmd = cmd.withPortBindings(portBindings);
			}
		}

		if (CommonUtil.hasElement(envs)) {
			cmd = cmd.withEnv(envs);
		}

		List<Link> linkList = new ArrayList<>();
		if (CommonUtil.hasElement(links)) {
			for (String link : links) {
				String[] split = link.split(":");
				String containerName = split[0];
				String alias = split[1];
				linkList.add(new Link(containerName, alias));
			}
		}
		if (!linkList.isEmpty()) {
			cmd = cmd.withLinks(linkList);
		}

		List<Bind> bindList = new ArrayList<>();
		List<Volume> volumeList = new ArrayList<>();
		if (CommonUtil.hasElement(volumes)) {
			for (String volume : volumes) {
				String[] split = volume.split(":");
				String hostPath = split[0];
				String innerPath = split[1];
				Volume innerVolume = new Volume(innerPath);
				volumeList.add(innerVolume);
				bindList.add(new Bind(hostPath, innerVolume));
			}
		}
		if (CommonUtil.hasElement(volumes)) {
			cmd = cmd.withVolumes(volumeList);
			cmd = cmd.withBinds(bindList);
		}

		if (CommonUtil.hasElement(extraHosts)) {
			cmd = cmd.withExtraHosts(extraHosts);
		}

		String containerId = cmd.exec().getId();
		logger.info("Created containerId {}", containerId);
		return containerId;
	}

	@Override
	public void removeContainer(String containerId) {
		try {
			dockerClient.removeContainerCmd(containerId).exec();
		} catch (NotModifiedException e) {
			logger.error("No such Container " + containerId);
		}
	}

	@Override
	public InspectContainerResponse inspectContainer(String containerId) {
		InspectContainerResponse containerResponse = dockerClient.inspectContainerCmd(containerId).exec();
		return containerResponse;
	}
}
