package com.hawa.hawa_backend.postprovider;

import java.time.LocalDate;
import java.util.List;

import com.hawa.hawa_backend.brand.Brand;
import com.hawa.hawa_backend.enums.DataSourceEnum;
import com.hawa.hawa_backend.post.Post;
import com.hawa.hawa_backend.report.Report;

public abstract class PostProvider {

    public abstract DataSourceEnum dataSource();

    public abstract List<Post> collect(Report report, Brand brand, LocalDate from, LocalDate to);
}
