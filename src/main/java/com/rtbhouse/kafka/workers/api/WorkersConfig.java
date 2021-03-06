package com.rtbhouse.kafka.workers.api;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.rtbhouse.kafka.workers.api.record.action.FailureActionName.FALLBACK_TOPIC;
import static com.rtbhouse.kafka.workers.api.record.action.FailureActionName.SHUTDOWN;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.metrics.MetricsReporter;

import com.rtbhouse.kafka.workers.api.partitioner.WorkerSubpartition;
import com.rtbhouse.kafka.workers.api.record.action.FailureActionName;
import com.rtbhouse.kafka.workers.api.task.WorkerTask;
import com.rtbhouse.kafka.workers.impl.consumer.ConsumerThread;
import com.rtbhouse.kafka.workers.impl.task.WorkerThread;

/**
 * {@link KafkaWorkers} configuration
 */
public class WorkersConfig extends AbstractConfig {

    /**
     * Should be used as a prefix for internal {@link KafkaConsumer} configuration used by {@link ConsumerThread}.
     */
    public static final String CONSUMER_PREFIX = "consumer.kafka.";

    /**
     * A list of kafka topics read by {@link ConsumerThread}.
     */
    public static final String CONSUMER_TOPICS = "consumer.topics";
    private static final String CONSUMER_TOPICS_DOC = "A list of kafka topics read by ConsumerThread.";

    /**
     * The timeout in milliseconds for {@link KafkaConsumer}'s poll().
     */
    public static final String CONSUMER_POLL_TIMEOUT_MS = "consumer.poll.timeout.ms";
    private static final String CONSUMER_POLL_TIMEOUT_MS_DOC = "The timeout in milliseconds for KafkaConsumer's poll().";
    private static final long CONSUMER_POLL_TIMEOUT_MS_DEFAULT = Duration.of(1, ChronoUnit.SECONDS).toMillis();

    /**
     * The frequency in milliseconds that the processed offsets are committed to Kafka.
     */
    public static final String CONSUMER_COMMIT_INTERVAL_MS = "consumer.commit.interval.ms";
    private static final String CONSUMER_COMMIT_INTERVAL_MS_DOC = "The frequency in milliseconds that the processed offsets are committed to Kafka.";
    private static final long CONSUMER_COMMIT_INTERVAL_MS_DEFAULT = Duration.of(10, ChronoUnit.SECONDS).toMillis();

    /**
     * The timeout in milliseconds for record to be successfully processed.
     */
    public static final String CONSUMER_PROCESSING_TIMEOUT_MS = "consumer.processing.timeout.ms";
    private static final String CONSUMER_PROCESSING_TIMEOUT_MS_DOC = "The timeout in milliseconds for record to be successfully processed.";
    private static final long CONSUMER_PROCESSING_TIMEOUT_MS_DEFAULT = Duration.of(5, ChronoUnit.MINUTES).toMillis();

    /**
     * The number of retries in case of retriable commit failed exception.
     */
    public static final String CONSUMER_MAX_RETRIABLE_FAILURES = "consumer.commit.retries";
    private static final String CONSUMER_MAX_RETRIABLE_FAILURES_DOC = "The number of retries in case of retriable commit failed exception.";
    private static final int CONSUMER_MAX_RETRIABLE_FAILURES_DEFAULT = 3;

    /**
     * The number of {@link WorkerThread}s per one {@link KafkaWorkers} instance.
     */
    public static final String WORKER_THREADS_NUM = "worker.threads.num";
    private static final String WORKER_THREADS_NUM_DOC = "The number of WorkerThreads per one Kafka Workers instance.";
    private static final int WORKER_THREADS_NUM_DEFAULT = 1;

    /**
     * The time in milliseconds to wait for {@link WorkerThread} in case of not accepted tasks.
     */
    public static final String WORKER_SLEEP_MS = "worker.sleep.ms";
    private static final String WORKER_SLEEP_MS_DOC = "The time in milliseconds to wait for WorkerThread in case of not accepted tasks.";
    private static final long WORKER_SLEEP_MS_DEFAULT = Duration.of(1, ChronoUnit.SECONDS).toMillis();

    /**
     * Could be used as a prefix for internal {@link WorkerTask} configuration.
     */
    public static final String WORKER_TASK_PREFIX = "worker.task.";

    /**
     * Max size in bytes for single {@link WorkerSubpartition}'s internal queue.
     */
    public static final String QUEUE_MAX_SIZE_BYTES = "queue.max.size.bytes";
    private static final String QUEUE_MAX_SIZE_BYTES_DOC = "Max size in bytes for single WorkerSubpartition's internal queue.";
    private static final long QUEUE_MAX_SIZE_BYTES_DEFAULT = 256 * 1024 * 1024;

    /**
     * Max total size in bytes for all internal queues.
     */
    public static final String QUEUE_TOTAL_MAX_SIZE_BYTES = "queue.total.max.size.bytes";
    private static final String QUEUE_TOTAL_MAX_SIZE_BYTES_DOC = "Total max size in bytes for all internal queues.";
    private static final Long QUEUE_TOTAL_MAX_SIZE_BYTES_DEFAULT = null;

    /**
     * A list of {@link MetricsReporter}s which report {@code KafkaWorkers}'s metrics.
     */
    public static final String METRIC_REPORTER_CLASSES = CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG;
    private static final String METRIC_REPORTER_CLASSES_DOC = CommonClientConfigs.METRIC_REPORTER_CLASSES_DOC;
    private static final String METRIC_REPORTER_CLASSES_DEFAULT = "";

    public static final String RECORD_PROCESSING_FAILURE_ACTION = "record.processing.failure.action";
    private static final String RECORD_PROCESSING_FAILURE_ACTION_DOC = "Allowed values: " +
            Arrays.toString(FailureActionName.values());
    private static final String RECORD_PROCESSING_FAILURE_ACTION_DEFAULT = SHUTDOWN.name();

    public static final String RECORD_PROCESSING_FALLBACK_TOPIC = "record.processing.fallback.topic";
    private static final String RECORD_PROCESSING_FALLBACK_TOPIC_DOC = String.format("Topic where records" +
            " will be sent in case of processing failure (%s = %s)", RECORD_PROCESSING_FAILURE_ACTION, FALLBACK_TOPIC);
    private static final String RECORD_PROCESSING_FALLBACK_TOPIC_DEFAULT = null;

    public static final String RECORD_PROCESSING_FALLBACK_KAFKA_PRODUCER_PREFIX = "record.processing.fallback.producer.kafka.";

    private static final ConfigDef CONFIG;
    static {
        CONFIG = new ConfigDef()
                .define(CONSUMER_TOPICS,
                        Type.LIST,
                        Importance.HIGH,
                        CONSUMER_TOPICS_DOC)
                .define(CONSUMER_POLL_TIMEOUT_MS,
                        Type.LONG,
                        CONSUMER_POLL_TIMEOUT_MS_DEFAULT,
                        Importance.LOW,
                        CONSUMER_POLL_TIMEOUT_MS_DOC)
                .define(CONSUMER_COMMIT_INTERVAL_MS,
                        Type.LONG,
                        CONSUMER_COMMIT_INTERVAL_MS_DEFAULT,
                        Importance.MEDIUM,
                        CONSUMER_COMMIT_INTERVAL_MS_DOC)
                .define(CONSUMER_PROCESSING_TIMEOUT_MS,
                        Type.LONG,
                        CONSUMER_PROCESSING_TIMEOUT_MS_DEFAULT,
                        Importance.MEDIUM,
                        CONSUMER_PROCESSING_TIMEOUT_MS_DOC)
                .define(CONSUMER_MAX_RETRIABLE_FAILURES,
                        Type.INT,
                        CONSUMER_MAX_RETRIABLE_FAILURES_DEFAULT,
                        Importance.LOW,
                        CONSUMER_MAX_RETRIABLE_FAILURES_DOC)
                .define(WORKER_THREADS_NUM,
                        Type.INT,
                        WORKER_THREADS_NUM_DEFAULT,
                        Importance.HIGH,
                        WORKER_THREADS_NUM_DOC)
                .define(WORKER_SLEEP_MS,
                        Type.LONG,
                        WORKER_SLEEP_MS_DEFAULT,
                        Importance.MEDIUM,
                        WORKER_SLEEP_MS_DOC)
                .define(QUEUE_MAX_SIZE_BYTES,
                        Type.LONG,
                        QUEUE_MAX_SIZE_BYTES_DEFAULT,
                        Importance.MEDIUM,
                        QUEUE_MAX_SIZE_BYTES_DOC)
                .define(QUEUE_TOTAL_MAX_SIZE_BYTES,
                        Type.LONG,
                        QUEUE_TOTAL_MAX_SIZE_BYTES_DEFAULT,
                        Importance.MEDIUM,
                        QUEUE_TOTAL_MAX_SIZE_BYTES_DOC)
                .define(METRIC_REPORTER_CLASSES,
                        Type.LIST,
                        METRIC_REPORTER_CLASSES_DEFAULT,
                        Importance.LOW,
                        METRIC_REPORTER_CLASSES_DOC)
                .define(RECORD_PROCESSING_FAILURE_ACTION,
                        Type.STRING,
                        RECORD_PROCESSING_FAILURE_ACTION_DEFAULT,
                        Importance.MEDIUM,
                        RECORD_PROCESSING_FAILURE_ACTION_DOC)
                .define(RECORD_PROCESSING_FALLBACK_TOPIC,
                        Type.STRING,
                        RECORD_PROCESSING_FALLBACK_TOPIC_DEFAULT,
                        Importance.MEDIUM,
                        RECORD_PROCESSING_FALLBACK_TOPIC_DOC);
    }

    private static final Map<String, Object> CONSUMER_CONFIG_FINALS;
    static {
        final Map<String, Object> tmpConfigs = new HashMap<>();
        tmpConfigs.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        CONSUMER_CONFIG_FINALS = Collections.unmodifiableMap(tmpConfigs);
    }

    public WorkersConfig(final Map<?, ?> props) {
        super(CONFIG, props);
        checkConfigFinals(CONSUMER_PREFIX, CONSUMER_CONFIG_FINALS);
        checkRecordProcessingConfigs();
    }

    private void checkConfigFinals(String prefix, Map<String, Object> finals) {
        Map<String, Object> configs = originalsWithPrefix(prefix);;
        for (Map.Entry<String, Object> override : finals.entrySet()) {
            var value = configs.get(override.getKey());
            checkState(value == null || value.equals(override.getValue()), "Config [%s] should be set to [%s]",
                    prefix + override.getKey(), override.getValue());
        }
    }

    private void checkRecordProcessingConfigs() {
        FailureActionName failureActionName = getFailureActionName();
        checkNotNull(failureActionName, "failureActionName cannot be null");
        if (failureActionName == FALLBACK_TOPIC) {
            var fallbackTopic = getString(RECORD_PROCESSING_FALLBACK_TOPIC);
            checkNotNull(fallbackTopic, "Missing [%s] parameter in configuration",
                    RECORD_PROCESSING_FALLBACK_TOPIC);
            var kafkaProducerConfigs = getFallbackKafkaProducerConfigs();
            checkState(kafkaProducerConfigs != null && !kafkaProducerConfigs.isEmpty(),
                    "Missing [%s*] parameter(s) in configuration",
                    RECORD_PROCESSING_FALLBACK_KAFKA_PRODUCER_PREFIX);
        }
    }

    public Map<String, Object> getConsumerConfigs() {
        return getConfigsWithFinals(CONSUMER_PREFIX, CONSUMER_CONFIG_FINALS);
    }

    private Map<String, Object> getConfigsWithFinals(String prefix, Map<String, Object> finals) {
        Map<String, Object> configs = originalsWithPrefix(prefix);
        for (Map.Entry<String, Object> override : finals.entrySet()) {
            configs.put(override.getKey(), override.getValue());
        };
        return configs;
    }

    public Map<String, Object> getWorkerTaskConfigs() {
        return originalsWithPrefix(WORKER_TASK_PREFIX);
    }

    public Map<String, Object> getFallbackKafkaProducerConfigs() {
        return originalsWithPrefix(RECORD_PROCESSING_FALLBACK_KAFKA_PRODUCER_PREFIX);
    }

    public FailureActionName getFailureActionName() {
        return FailureActionName.of(getString(RECORD_PROCESSING_FAILURE_ACTION));
    }

}
