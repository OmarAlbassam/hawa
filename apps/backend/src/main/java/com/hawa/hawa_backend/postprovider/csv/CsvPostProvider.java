package com.hawa.hawa_backend.postprovider.csv;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.post.PostRepository;
import com.hawa.hawa_backend.postprovider.PostProvider;
import com.hawa.hawa_backend.report.Report;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CsvPostProvider extends PostProvider {

    private final PostRepository postRepository;

    @Override
    public DataSourceEnum dataSource() {
        return DataSourceEnum.CSV_UPLOAD;
    }

    @Override
    public List<Post> collect(Report report, Brand brand, LocalDate from, LocalDate to) {
        return postRepository.findByReport_ReportId(report.getReportId());
    }
}
