package org.ironrhino.core.elasticsearch.search;

import java.util.List;

import org.ironrhino.core.elasticsearch.Constants;
import org.ironrhino.rest.client.JsonPointer;
import org.ironrhino.rest.client.RestApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestApi(apiBaseUrl = Constants.ELASTICSEARCH_URL)
public interface SearchOperations<T> {

	@GetMapping("/{index}/_search")
	@JsonPointer("/hits/hits")
	List<SearchHits<T>> search(@PathVariable String index, @RequestParam("q") String query);

	@GetMapping("/{index}/_search")
	@JsonPointer("/hits/hits")
	List<SearchHits<T>> search(@PathVariable String index, @RequestParam("q") String query, @RequestParam int from,
			@RequestParam int size);

}