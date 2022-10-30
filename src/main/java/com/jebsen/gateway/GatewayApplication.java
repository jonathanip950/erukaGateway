package com.jebsen.gateway;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.Scheduled;

import com.jebsen.broker.repository.BrokerBatchRepository;
import com.jebsen.broker.repository.BrokerDataRepository;
import com.jebsen.broker.repository.BrokerJobRepository;
import com.jebsen.repository.HttpTrafficRepository;

import lombok.extern.slf4j.Slf4j;

//@EnableFeignClients(basePackages = { "com.jebsen.*" })
@EnableDiscoveryClient
@SpringBootApplication
@Slf4j
@EnableScheduling
public class GatewayApplication {

	@Value("${jebsen.postgres.houseKeep.trafficRecord.houseKeep:30}")
	private int trafficRecordHouseKeep;
	@Value("${jebsen.postgres.houseKeep.batchRecord.houseKeep:30}")
	private int batchRecordHouseKeep;
	@Value("${jebsen.postgres.houseKeep.jobRecord.houseKeep:30}")
	private int jobRecordHouseKeep;
	@Value("${jebsen.postgres.houseKeep.dataRecord.houseKeep:30}")
	private int dataRecordHouseKeep;
	
	@Autowired
	private HttpTrafficRepository httpTrafficRepository;
	@Autowired
	private BrokerBatchRepository brokerBatchRepository;
	@Autowired
	private BrokerJobRepository brokerJobRepository;
	@Autowired
	private BrokerDataRepository brokerDataRepository;
	
	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Scheduled(cron="${jebsen.postgres.houseKeep.trafficRecord.cron:0 0 0 * * ?}")
	public void houseKeepTrafficRecord() {
		log.info("trafficRecordHouseKeep:"+this.trafficRecordHouseKeep);
		if(this.trafficRecordHouseKeep >= 0) {
			try {
				this.httpTrafficRepository.deleteByDatetime(this.convertDatetimeFromNow(this.trafficRecordHouseKeep));
			}catch(Exception e) {
				log.error("houseKeepTrafficRecord error", e);
			}
		}
	}
	
	@Scheduled(cron="${jebsen.postgres.houseKeep.batchRecord.cron:0 0 0 * * ?}")
	public void houseKeepBatchRecord() {
		log.info("batchRecordHouseKeep:"+this.batchRecordHouseKeep);
		if(this.batchRecordHouseKeep >= 0) {
			try {
				this.brokerBatchRepository.deleteByDatetime(this.convertDatetimeFromNow(this.batchRecordHouseKeep));
			}catch(Exception e) {
				log.error("houseKeepBatchRecord error", e);
			}
		}
	}
	
	@Scheduled(cron="${jebsen.postgres.houseKeep.jobRecord.cron:0 0 0 * * ?}")
	public void houseKeepJobRecord() {
		log.info("jobRecordHouseKeep:"+this.jobRecordHouseKeep);
		if(this.jobRecordHouseKeep >= 0) {
			try {
				this.brokerJobRepository.deleteByDatetime(this.convertDatetimeFromNow(this.jobRecordHouseKeep));
			}catch(Exception e) {
				log.error("houseKeepJobRecord error", e);
			}
		}
	}
	
	@Scheduled(cron="${jebsen.postgres.houseKeep.dataRecord.cron:0 0 0 * * ?}")
	public void houseKeepDataRecord() {
		log.info("dataRecordHouseKeep:"+this.dataRecordHouseKeep);
		if(this.dataRecordHouseKeep >= 0 ) {
			try {
				this.brokerDataRepository.deleteByDatetime(this.convertDatetimeFromNow(this.dataRecordHouseKeep));
			}catch(Exception e) {
				log.error("houseKeepDataRecord error", e);
			}
		}
	}
	
	private Timestamp convertDatetimeFromNow(int houseKeep) throws Exception {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd 00:00:00");
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(sdf.parse(sdf.format(new Date())).getTime());
		c.add(Calendar.DATE, houseKeep*-1);
		return new Timestamp(c.getTimeInMillis());
	}
}
