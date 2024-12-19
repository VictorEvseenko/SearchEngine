package searchengine.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchResponse
{
    private Boolean result;
    private Integer count;
    private List<DataSearchResponse> data;
}
