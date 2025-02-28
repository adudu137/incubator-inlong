/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.agent.core.job;

import org.apache.inlong.agent.common.AbstractDaemon;
import org.apache.inlong.agent.common.AgentThreadFactory;
import org.apache.inlong.agent.conf.AgentConfiguration;
import org.apache.inlong.agent.conf.JobProfile;
import org.apache.inlong.agent.constant.AgentConstants;
import org.apache.inlong.agent.core.AgentManager;
import org.apache.inlong.agent.db.JobProfileDb;
import org.apache.inlong.agent.db.StateSearchKey;
import org.apache.inlong.agent.utils.AgentUtils;
import org.apache.inlong.agent.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.inlong.agent.constant.AgentConstants.DEFAULT_JOB_DB_CACHE_CHECK_INTERVAL;
import static org.apache.inlong.agent.constant.AgentConstants.DEFAULT_JOB_DB_CACHE_TIME;
import static org.apache.inlong.agent.constant.AgentConstants.DEFAULT_JOB_NUMBER_LIMIT;
import static org.apache.inlong.agent.constant.AgentConstants.JOB_DB_CACHE_CHECK_INTERVAL;
import static org.apache.inlong.agent.constant.AgentConstants.JOB_DB_CACHE_TIME;
import static org.apache.inlong.agent.constant.AgentConstants.JOB_NUMBER_LIMIT;
import static org.apache.inlong.agent.constant.JobConstants.JOB_ID;
import static org.apache.inlong.agent.constant.JobConstants.JOB_ID_PREFIX;
import static org.apache.inlong.agent.constant.JobConstants.JOB_INSTANCE_ID;
import static org.apache.inlong.agent.constant.JobConstants.SQL_JOB_ID;

/**
 * JobManager maintains lots of jobs, and communicate between server and task manager.
 */
public class JobManager extends AbstractDaemon {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobManager.class);
    // jobs which are not accepted by running pool.
    private final ConcurrentHashMap<String, Job> pendingJobs;
    // job thread pool
    private final ThreadPoolExecutor runningPool;
    private final AgentManager agentManager;
    private final int monitorInterval;
    private final long jobDbCacheTime;
    private final long jobDbCacheCheckInterval;
    // job profile db is only used to recover instance which is not finished running.
    private final JobProfileDb jobProfileDb;
    private final JobMetrics jobMetrics;
    private final AtomicLong index = new AtomicLong(0);
    private final long jobMaxSize;
    // key is job instance id.
    private ConcurrentHashMap<String, JobWrapper> jobs;

    /**
     * init job manager
     *
     * @param agentManager agent manager
     */
    public JobManager(AgentManager agentManager, JobProfileDb jobProfileDb) {
        this.jobProfileDb = jobProfileDb;
        this.agentManager = agentManager;
        // job thread pool for running
        this.runningPool = new ThreadPoolExecutor(
                0, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new AgentThreadFactory("job"));
        this.jobs = new ConcurrentHashMap<>();
        this.pendingJobs = new ConcurrentHashMap<>();
        AgentConfiguration conf = AgentConfiguration.getAgentConf();
        this.monitorInterval = conf
                .getInt(
                        AgentConstants.JOB_MONITOR_INTERVAL, AgentConstants.DEFAULT_JOB_MONITOR_INTERVAL);
        this.jobDbCacheTime = conf.getLong(JOB_DB_CACHE_TIME, DEFAULT_JOB_DB_CACHE_TIME);
        this.jobDbCacheCheckInterval = conf.getLong(JOB_DB_CACHE_CHECK_INTERVAL, DEFAULT_JOB_DB_CACHE_CHECK_INTERVAL);
        this.jobMaxSize = conf.getLong(JOB_NUMBER_LIMIT, DEFAULT_JOB_NUMBER_LIMIT);

        if (ConfigUtil.isPrometheusEnabled()) {
            this.jobMetrics = new JobPrometheusMetrics();
        } else {
            this.jobMetrics = JobJmxMetrics.create();
        }
    }

    /**
     * submit job to work thread.
     *
     * @param job job
     */
    private void addJob(Job job) {
        try {
            JobWrapper jobWrapper = new JobWrapper(agentManager, job);
            this.runningPool.execute(jobWrapper);
            JobWrapper jobWrapperRet = jobs.putIfAbsent(jobWrapper.getJob().getJobInstanceId(), jobWrapper);
            if (jobWrapperRet != null) {
                LOGGER.warn("{} has been added to running pool, "
                        + "cannot be added repeatedly", job.getJobInstanceId());
            } else {
                jobMetrics.incRunningJobCount();
            }
        } catch (Exception rje) {
            LOGGER.debug("reject job {}", job.getJobInstanceId(), rje);
            pendingJobs.putIfAbsent(job.getJobInstanceId(), job);
        }
    }

    /**
     * add file job profile
     *
     * @param profile job profile.
     */
    public boolean submitFileJobProfile(JobProfile profile) {
        return submitJobProfile(profile, false);
    }

    /**
     * add file job profile
     *
     * @param profile job profile.
     */
    public boolean submitJobProfile(JobProfile profile, boolean singleJob) {
        if (!isJobValid(profile)) {
            return false;
        }
        String jobId = profile.get(JOB_ID);
        if (singleJob) {
            profile.set(JOB_INSTANCE_ID, jobId);
        } else {
            profile.set(JOB_INSTANCE_ID, AgentUtils.getUniqId(JOB_ID_PREFIX, jobId, index.incrementAndGet()));
        }
        LOGGER.info("submit job profile {}", profile.toJsonStr());
        getJobConfDb().storeJobFirstTime(profile);
        addJob(new Job(profile));
        return true;
    }

    /**
     * add sql job profile
     *
     * @param profile job profile.
     */
    public boolean submitSqlJobProfile(JobProfile profile) {
        if (isJobValid(profile)) {
            return false;
        }
        profile.set(JOB_INSTANCE_ID, SQL_JOB_ID);
        LOGGER.info("submit job profile {}", profile.toJsonStr());
        getJobConfDb().storeJobFirstTime(profile);
        addJob(new Job(profile));
        return true;
    }

    private boolean isJobValid(JobProfile profile) {
        if (profile == null || !profile.allRequiredKeyExist()) {
            LOGGER.error("profile is null or not all required key exists {}", profile == null ? null
                    : profile.toJsonStr());
            return false;
        }
        if (isJobOverLimit()) {
            LOGGER.error("agent cannot add more job, max job size is {}", jobMaxSize);
            return false;
        }
        return true;
    }

    /**
     * whether job size exceeds maxSize
     */
    public boolean isJobOverLimit() {
        return jobs.size() >= jobMaxSize;
    }

    /**
     * delete job profile and stop job thread
     *
     * @param jobInstancId
     */
    public boolean deleteJob(String jobInstancId) {
        LOGGER.info("start to delete job, job id set {}", jobs.keySet());
        JobWrapper jobWrapper = jobs.remove(jobInstancId);
        if (jobWrapper != null) {
            LOGGER.info("delete job instance with job id {}", jobInstancId);
            jobWrapper.cleanup();
            getJobConfDb().deleteJob(jobInstancId);
            return true;
        }
        return false;
    }

    /**
     * start all accepted jobs.
     */
    private void startJobs() {
        List<JobProfile> profileList = getJobConfDb().getRestartJobs();
        for (JobProfile profile : profileList) {
            LOGGER.info("init starting job from db {}", profile.toJsonStr());
            addJob(new Job(profile));
        }
    }

    /**
     * check pending jobs and submit them
     */
    public Runnable jobStateCheckThread() {
        return () -> {
            while (isRunnable()) {
                try {
                    // check pending jobs and try to submit again.
                    for (String jobId : pendingJobs.keySet()) {
                        Job job = pendingJobs.remove(jobId);
                        if (job != null) {
                            addJob(job);
                        }
                    }
                    TimeUnit.SECONDS.sleep(monitorInterval);
                } catch (Exception ex) {
                    LOGGER.error("error caught", ex);
                }
            }
        };
    }

    /**
     * check local db and delete old tasks.
     */
    public Runnable dbStorageCheckThread() {
        return () -> {
            while (isRunnable()) {
                try {
                    jobProfileDb.removeExpireJobs(jobDbCacheTime);
                } catch (Exception ex) {
                    LOGGER.error("removeExpireJobs error caught", ex);
                }
                try {
                    TimeUnit.SECONDS.sleep(jobDbCacheCheckInterval);
                } catch (Exception ex) {
                    LOGGER.error("sleep error caught", ex);
                }
            }
        };
    }

    /**
     * mark job as success by job id.
     *
     * @param jobId job id
     */
    public void markJobAsSuccess(String jobId) {
        JobWrapper wrapper = jobs.remove(jobId);
        if (wrapper != null) {
            jobMetrics.decRunningJobCount();
            LOGGER.info("job instance {} is success", jobId);
            // mark job as success.
            jobProfileDb.updateJobState(jobId, StateSearchKey.SUCCESS);
        }
    }

    /**
     * remove job from jobs, and mark it as failed
     *
     * @param jobId job id
     */
    public void markJobAsFailed(String jobId) {
        JobWrapper wrapper = jobs.remove(jobId);
        if (wrapper != null) {
            LOGGER.info("job instance {} is failed", jobId);
            jobMetrics.decRunningJobCount();
            jobMetrics.incFatalJobCount();
            // mark job as success.
            jobProfileDb.updateJobState(jobId, StateSearchKey.FAILED);
        }
    }

    public JobProfileDb getJobConfDb() {
        return jobProfileDb;
    }

    /**
     * check job existence using job file name
     */
    public boolean checkJobExsit(String fileName) {
        return jobProfileDb.getJobByFileName(fileName) != null;
    }

    /**
     * get sql job existence
     */
    public boolean sqlJobExsit() {
        return jobProfileDb.getJobById(SQL_JOB_ID) != null;
    }

    public Map<String, JobWrapper> getJobs() {
        return jobs;
    }

    @Override
    public void start() {
        submitWorker(jobStateCheckThread());
        submitWorker(dbStorageCheckThread());
        startJobs();
    }

    @Override
    public void stop() throws Exception {
        waitForTerminate();
        this.runningPool.shutdown();
    }
}
