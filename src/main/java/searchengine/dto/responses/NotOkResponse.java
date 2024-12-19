package searchengine.dto.responses;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotOkResponse
{
    private Boolean result;
    private String error;
}
