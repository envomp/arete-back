package ee.taltech.arete_admin_panel.domain;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@Builder
@Table(name = "student")
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Size(min = 1)
    @NotNull
    @Builder.Default
    private String uniid = "NaN"; // TalTech student identificator: envomp - Ago guarantee to be unique

    private String gitRepo;

    @NotNull
    private Long firstTested;

    @NotNull
    private Long lastTested;

    @NotNull
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    private Set<Long> timestamps = new HashSet<>();

    @NotNull
    @Builder.Default
    @ElementCollection
    private Set<String> courses = new HashSet<>();

    @NotNull
    @Builder.Default
    @ElementCollection
    private Set<String> slugs = new HashSet<>();

    @NotNull
    @Builder.Default
    private Integer totalCommits = 0;

    @NotNull
    @Builder.Default
    private Integer totalTestsRan = 0;

    @NotNull
    @Builder.Default
    private Integer totalTestsPassed = 0;

    @NotNull
    @Builder.Default
    private Integer totalDiagnosticErrors = 0;

    @NotNull
    @Builder.Default
    private Integer totalTestErrors = 0;

    @NotNull
    @Builder.Default
    private Integer differentSlugs = 0;

    @NotNull
    @Builder.Default
    private Integer differentCourses = 0;

    @NotNull
    @Builder.Default
    private Integer commitsStyleOK = 0;

}
