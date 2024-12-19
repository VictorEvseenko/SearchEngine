package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import javax.persistence.Index;

@Setter
@Getter
@Entity
@ToString
@Table(name = "page", indexes = @Index(name = "path_index", columnList = "path"))
public class Page
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity siteEntity;

    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
