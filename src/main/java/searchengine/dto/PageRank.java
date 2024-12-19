package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import searchengine.model.Page;

@Data
@AllArgsConstructor
public class PageRank
{
    private Page page;
    private float rank;
}
