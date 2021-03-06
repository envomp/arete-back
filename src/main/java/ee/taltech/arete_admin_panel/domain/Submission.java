package ee.taltech.arete_admin_panel.domain;

import lombok.*;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "submission")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private String testingPlatform;

    @NotNull
    private String hash; // references to job.

    private String slug;

    @Builder.Default
    @NotNull
    private String uniid = "NaN"; // gitlab namespace: envomp

    private String root; // gitlab path for student: iti0102-2019

    private String gitStudentRepo;

    private String gitTestSource;

    @NotNull
    private Long timestamp;

	@NotNull
	@Builder.Default
	private boolean failed = false;

}
