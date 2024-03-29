package com.demo.springdemo.service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.demo.springdemo.entity.Request;
import com.demo.springdemo.repository.RequestRepository;

@Service
public class RequestService{
	@Autowired 
	private RequestRepository repository;
	boolean isRetry = false;
	boolean isPush = false;
	
	public List<Request> saveAll(List<Request> requests) {
		return repository.saveAll(requests);
	}

	public void readLogFile(Path path, List<String> allDataList) {
		try (var oneLineData = Files.lines(path)) {
			oneLineData.forEach(item -> {
				allDataList.add(item);
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void readLogZipFile(File file, List<String> allDataList) {
		try (ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(file))) {
			ZipEntry zipEntry;
			while ((zipEntry = zipInputStream.getNextEntry()) != null) {
				if (zipEntry.getName().endsWith(".log")) {
					// System.out.println("cac file log trong file zip la: " + zipEntry.getName());
					// read log file inside zip
					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipInputStream));
					String lineString;
					while ((lineString = bufferedReader.readLine()) != null) {
						// System.out.println(lineString);
						var oneLineData = lineString.split("\n");
						for (String data : oneLineData) {
							// System.out.println("data: "+ data);
							allDataList.add(data);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public List<String> findErrorId(List<String> allDataList) {
		List<String> errorIdArrList = new ArrayList<>();
		for (String dataLine : allDataList) {
			if (dataLine.contains("[ERROR]")) {
				String[] errorLine = dataLine.split(" ");
				String errorID = errorLine[4];
				errorIdArrList.add(errorID);
			}
		}
		return errorIdArrList;
	}

	public List<String> getLogStrings(List<String> allDataList) {
		List<String> lstErrorIdList = findErrorId(allDataList);

		try {
			List<String> lstDataList = allDataList.stream()
					.filter(oneLine -> !oneLine.equals("[ERROR]")
							&& oneLine.contains("[sendIPNReceipt]") && oneLine.contains("Request"))
					.peek(oneLine -> {
						if (oneLine.contains("retry")) {
							isRetry = true;
						}
						if (oneLine.contains("status: 200")) {
							isPush = true;
						}
					})
					.filter(oneLine -> lstErrorIdList.stream().anyMatch(oneLine::contains))
					.collect(Collectors.toList());
			return lstDataList;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<String> getLogRequeStrings(List<String> allDataList) {
		List<String> lstRequestString = new ArrayList<>();
		List<String> lstErrorRequestStrings = getLogStrings(allDataList);
		try {
			lstErrorRequestStrings.forEach(item -> {
				String[] LogArr = item.split(" ");
				String request = LogArr[4]+"///"+LogArr[7];

				lstRequestString.add(request);
			});
			return lstRequestString;
		} catch (Exception e) {

		}
		return null;
	}

	public List<Request> AddRequestToDB(List<String> allDataList) {
		try {
			List<String> data = getLogRequeStrings(allDataList);

			ObjectMapper objectMapper = new ObjectMapper();
			List<Request> requestList = new ArrayList<>();
			data.forEach(item -> {
				Map<String, Object> requestObject;
				String[] LogArr = item.split("///");
				try {
					requestObject = objectMapper.readValue(LogArr[1], Map.class);

					String url = (String) requestObject.get("url");
					String method = (String) requestObject.get("method");
					String json = objectMapper.writeValueAsString(requestObject.get("json"));

					Request request = new Request();
					request.setUrl(url);
					request.setRequestId(LogArr[0]);
					request.setMethod(method);
					request.setJsonData(json);
					request.setCreateDate(new Date());
					request.setUpdateDate(new Date());
					request.setRetry(isRetry);
					request.setPush(isPush);

					requestList.add(request);

				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}

			});
			return requestList;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public String copyFilesFromRemoteToLocal(){
		String remoteUsername = "hoang";
		String password = "hoangkktt123";
		String remoteHost = "hoang-virtual-machine";
		String localFilePath = "C:\\Users\\huyho\\Pc\\Desktop\\SampleData";
		String remoteFilePath = "/home/hoang/Desktop/SampleData";

		try {
			JSch jSch = new JSch();
			Session session = jSch.getSession(remoteUsername, remoteHost, 22);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
			if(session.isConnected()){
				System.out.println("Da connect server thanh cong!!!");
				ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
				channelSftp.connect();

				channelSftp.cd(remoteFilePath);

				@SuppressWarnings("unchecked")
				Vector<ChannelSftp.LsEntry> list = channelSftp.ls("*");

				for (ChannelSftp.LsEntry entry : list) {
					if (!entry.getAttrs().isDir()) {
						String remoteFileName = entry.getFilename();
						InputStream remoteFileInputStream = channelSftp.get(remoteFileName);

						File localFile = new File(localFilePath, remoteFileName);
						FileOutputStream localFileOutputStream = new FileOutputStream(localFile);
						byte[] buffer = new byte[1024];
						int bytesRead;
						while ((bytesRead = remoteFileInputStream.read(buffer)) != -1) {
							localFileOutputStream.write(buffer, 0, bytesRead);
						}

						localFileOutputStream.close();
						remoteFileInputStream.close();
					}
				}

				channelSftp.disconnect();
				return localFilePath;
			}

			session.disconnect();
			System.out.println("Da disconnect server thanh cong!!!");
		}catch (Exception ex){
			ex.printStackTrace();
		}
		return null;
	}
	
}
