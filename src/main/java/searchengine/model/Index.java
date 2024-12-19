package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;

@Setter
@ToString
@Getter
@Entity
@Table(name = "index_search")
public class Index
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(name = "count_lemma", nullable = false)
    private float countLemma;
}
