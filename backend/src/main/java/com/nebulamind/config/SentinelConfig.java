package com.nebulamind.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@Aspect
public class SentinelConfig {

    @PostConstruct
    public void init() {
        initFlowRules();
        initDegradeRules();
    }

    @Pointcut("execution(* com.nebulamind.controller.*.*(..))")
    public void controllerMethods() {}

    @Around("controllerMethods()")
    public Object sentinelAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String resourceName = joinPoint.getSignature().getName();
        try {
            com.alibaba.csp.sentinel.Entry entry = com.alibaba.csp.sentinel.SphU.entry(resourceName);
            try {
                return joinPoint.proceed();
            } finally {
                entry.exit();
            }
        } catch (BlockException e) {
            log.warn("Sentinel block exception for resource: {}", resourceName);
            throw e;
        }
    }

    private void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule fileUploadRule = new FlowRule();
        fileUploadRule.setResource("initUpload");
        fileUploadRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        fileUploadRule.setCount(10);
        fileUploadRule.setLimitApp("default");
        rules.add(fileUploadRule);

        FlowRule fileUploadChunkRule = new FlowRule();
        fileUploadChunkRule.setResource("uploadChunk");
        fileUploadChunkRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        fileUploadChunkRule.setCount(50);
        fileUploadChunkRule.setLimitApp("default");
        rules.add(fileUploadChunkRule);

        FlowRule searchRule = new FlowRule();
        searchRule.setResource("search");
        searchRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        searchRule.setCount(20);
        searchRule.setLimitApp("default");
        rules.add(searchRule);

        FlowRule qaRule = new FlowRule();
        qaRule.setResource("ask");
        qaRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        qaRule.setCount(15);
        qaRule.setLimitApp("default");
        rules.add(qaRule);

        FlowRule classifyRule = new FlowRule();
        classifyRule.setResource("classifyFile");
        classifyRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        classifyRule.setCount(10);
        classifyRule.setLimitApp("default");
        rules.add(classifyRule);

        FlowRule generateRule = new FlowRule();
        generateRule.setResource("generateSummary");
        generateRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        generateRule.setCount(10);
        generateRule.setLimitApp("default");
        rules.add(generateRule);

        FlowRule generateReportRule = new FlowRule();
        generateReportRule.setResource("generateReport");
        generateReportRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        generateReportRule.setCount(5);
        generateReportRule.setLimitApp("default");
        rules.add(generateReportRule);

        FlowRule generatePPTRule = new FlowRule();
        generatePPTRule.setResource("generatePPT");
        generatePPTRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        generatePPTRule.setCount(5);
        generatePPTRule.setLimitApp("default");
        rules.add(generatePPTRule);

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel flow rules initialized: {}", rules.size());
    }

    private void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        DegradeRule searchDegradeRule = new DegradeRule();
        searchDegradeRule.setResource("search");
        searchDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        searchDegradeRule.setCount(3000);
        searchDegradeRule.setTimeWindow(30);
        rules.add(searchDegradeRule);

        DegradeRule qaDegradeRule = new DegradeRule();
        qaDegradeRule.setResource("ask");
        qaDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        qaDegradeRule.setCount(5000);
        qaDegradeRule.setTimeWindow(30);
        rules.add(qaDegradeRule);

        DegradeRule generateDegradeRule = new DegradeRule();
        generateDegradeRule.setResource("generateSummary");
        generateDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        generateDegradeRule.setCount(8000);
        generateDegradeRule.setTimeWindow(60);
        rules.add(generateDegradeRule);

        DegradeRule classifyDegradeRule = new DegradeRule();
        classifyDegradeRule.setResource("classifyFile");
        classifyDegradeRule.setGrade(RuleConstant.DEGRADE_GRADE_RT);
        classifyDegradeRule.setCount(5000);
        classifyDegradeRule.setTimeWindow(30);
        rules.add(classifyDegradeRule);

        DegradeRuleManager.loadRules(rules);
        log.info("Sentinel degrade rules initialized: {}", rules.size());
    }
}
