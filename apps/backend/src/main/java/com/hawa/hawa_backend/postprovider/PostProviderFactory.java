package com.hawa.hawa_backend.postprovider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.hawa.hawa_backend.enums.DataSourceEnum;

@Component
public class PostProviderFactory {

    private final Map<DataSourceEnum, PostProvider> providers;

    public PostProviderFactory(List<PostProvider> providers) {
        Map<DataSourceEnum, PostProvider> map = new EnumMap<>(DataSourceEnum.class);
        for (PostProvider provider : providers) {
            map.put(provider.dataSource(), provider);
        }
        this.providers = Map.copyOf(map);
    }

    public PostProvider forDataSource(DataSourceEnum dataSource) {
        PostProvider provider = providers.get(dataSource);
        if (provider == null) {
            throw new IllegalStateException("No PostProvider registered for data source: " + dataSource);
        }
        return provider;
    }
}
