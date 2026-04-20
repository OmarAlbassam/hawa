package com.hawa.hawa_backend.post.collector;

import java.time.LocalDate;
import java.util.List;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.report.Report;

public interface PostCollector {

    DataSourceEnum dataSource();

    List<Post> collect(Report report, Brand brand, LocalDate from, LocalDate to);
}
