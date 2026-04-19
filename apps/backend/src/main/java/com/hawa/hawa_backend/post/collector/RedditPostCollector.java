package com.hawa.hawa_backend.post.collector;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.report.Report;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RedditPostCollector implements PostCollector {

    @Override
    public DataSourceEnum dataSource() {
        return DataSourceEnum.REDDIT;
    }

    @Override
    public List<Post> collect(Report report, Brand brand, LocalDate from, LocalDate to) {
        // TODO: integrate PRAW-backed Reddit collector service.
        log.warn("RedditPostCollector is a stub — no posts will be collected for reportId={} brand={}",
                report.getReportId(), brand.getBrandName());
        return List.of();
    }
}
