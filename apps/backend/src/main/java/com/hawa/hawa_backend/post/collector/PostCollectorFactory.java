package com.hawa.hawa_backend.post.collector;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.enums.DataSourceEnum;

@Component
public class PostCollectorFactory {

    private final Map<DataSourceEnum, PostCollector> collectors;

    public PostCollectorFactory(List<PostCollector> collectors) {
        Map<DataSourceEnum, PostCollector> map = new EnumMap<>(DataSourceEnum.class);
        for (PostCollector collector : collectors) {
            map.put(collector.dataSource(), collector);
        }
        this.collectors = Map.copyOf(map);
    }

    public PostCollector forDataSource(DataSourceEnum dataSource) {
        PostCollector collector = collectors.get(dataSource);
        if (collector == null) {
            throw new IllegalStateException("No PostCollector registered for data source: " + dataSource);
        }
        return collector;
    }
}
