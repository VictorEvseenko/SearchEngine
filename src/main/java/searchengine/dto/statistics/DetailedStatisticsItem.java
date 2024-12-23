package searchengine.dto.statistics;

import lombok.Data;

import java.time.Instant;

@Data
public class DetailedStatisticsItem
{
    private String url;
    private String name;
    private String status;
    private Instant statusTime;
    private String error;
    private int pages;
    private int lemmas;
}
