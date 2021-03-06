package ee.taltech.arete_admin_panel.service;

import ee.taltech.arete.java.LoadBalancerClient;
import ee.taltech.arete.java.request.AreteRequestDTO;
import ee.taltech.arete.java.request.hook.AreteTestUpdateDTO;
import ee.taltech.arete.java.request.hook.CommitDTO;
import ee.taltech.arete.java.response.arete.*;
import ee.taltech.arete_admin_panel.domain.*;
import ee.taltech.arete_admin_panel.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class AreteService {

	private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final CacheService cacheService;
	private final StudentRepository studentRepository;
	private final CourseRepository courseRepository;
	private final SlugRepository slugRepository;
	private final SubmissionRepository submissionRepository;
	private final JobRepository jobRepository;
	private final LoadBalancerClient areteClient;

	@SneakyThrows
	public void parseAreteResponseDTO(AreteResponseDTO response) {
		logger.info("Saving job into DB for user: {} with hash: {} in: {}", response.getUniid(), response.getHash(), response.getRoot());

		setDefaultValuesIfNull(response);
		saveSubmission(response);
		saveJob(response);

		if (!response.getFailed()) {
			logger.debug("getting course");
			Course course = getCourse(response);

			logger.debug("getting slug");
			Slug slug = getSlug(response, course);

			logger.debug("getting student");
			Student student = getStudent(response, course, slug);

			logger.debug("update all");
			updateStudentSlugCourse(response, student, slug, course);
		}

	}

	private void setDefaultValuesIfNull(AreteResponseDTO response) {
		if (response.getUniid() == null) {
			response.setUniid("NaN");
		}

		if (response.getErrors() == null) {
			response.setErrors(new ArrayList<>());
		}

		if (response.getFiles() == null) {
			response.setFiles(new ArrayList<>());
		}

		if (response.getTestFiles() == null) {
			response.setTestFiles(new ArrayList<>());
		}

		if (response.getTestSuites() == null) {
			response.setTestSuites(new ArrayList<>());
		}

		if (response.getOutput() == null) {
			response.setOutput("no output");
		}

		if (response.getRoot() == null) {
			response.setRoot(response.getSlug());
		}

		if (response.getTimestamp() == null) {
			response.setTimestamp(System.currentTimeMillis());
		}
	}

	public Submission saveSubmission(AreteResponseDTO response) {
		Submission submission = Submission.builder()
				.uniid(response.getUniid())
				.slug(response.getSlug())
				.hash(response.getHash())
				.testingPlatform(response.getTestingPlatform())
				.root(response.getRoot())
				.timestamp(response.getTimestamp())
				.gitStudentRepo(response.getGitStudentRepo())
				.gitTestSource(response.getGitTestRepo())
				.failed(response.getFailed())
				.build();

		updateSubmissions(submission, submission.getHash());
		return submission;
	}

	private void saveJob(AreteResponseDTO response) {
		Job job = Job.builder()
				.output(response.getOutput().replace("\n", "<br>"))
				.consoleOutput(response.getConsoleOutputs().replace("\n", "<br>"))
				.testSuites(response.getTestSuites().stream()
						.map(x -> TestContext.builder()
								.endDate(x.getEndDate())
								.file(x.getFile())
								.grade(x.getGrade())
								.name(x.getName())
								.passedCount(x.getPassedCount())
								.startDate(x.getStartDate())
								.weight(x.getWeight())
								.unitTests(
										x.getUnitTests().stream()
												.map(y -> UnitTest.builder()
														.exceptionClass(y.getExceptionClass())
														.exceptionMessage(y.getExceptionMessage())
														.groupsDependedUpon(y.getGroupsDependedUpon())
														.methodsDependedUpon(y.getMethodsDependedUpon())
														.printExceptionMessage(y.getPrintExceptionMessage())
														.printStackTrace(y.getPrintStackTrace())
														.stackTrace(y.getStackTrace())
														.name(y.getName())
														.status(y.getStatus().toString())
														.timeElapsed(y.getTimeElapsed())
														.weight(y.getWeight())
														.build())
												.collect(Collectors.toList()))
								.build())
						.collect(Collectors.toList()))
				.timestamp(response.getTimestamp())
				.uniid(response.getUniid())
				.slug(response.getSlug())
				.root(response.getRoot())
				.testingPlatform(response.getTestingPlatform())
				.priority(response.getPriority())
				.hash(response.getHash())
				.commitMessage(response.getCommitMessage())
				.failed(response.getFailed())
				.gitStudentRepo(response.getGitStudentRepo())
				.gitTestRepo(response.getGitTestRepo())
				.dockerTimeout(response.getDockerTimeout())
				.dockerExtra(Set.of(response.getDockerExtra()))
				.systemExtra(response.getSystemExtra())
				.build();


		logger.info("Saving job");
		jobRepository.save(job);
	}

	private Course getCourse(AreteResponseDTO response) {
		Course course;
		Optional<Course> optionalCourse = courseRepository.findByGitUrl(response.getGitTestRepo());
		course = optionalCourse.orElseGet(() -> Course.builder()
				.gitUrl(response.getGitTestRepo())
				.name(response.getRoot())
				.build());

		return course;
	}

	private Slug getSlug(AreteResponseDTO response, Course course) {
		Slug slug;
		Optional<Slug> optionalSlug = slugRepository.findByCourseUrlAndName(course.getGitUrl(), response.getSlug());
		slug = optionalSlug.orElseGet(() -> Slug.builder()
				.courseUrl(course.getGitUrl())
				.name(response.getSlug())
				.build());

		return slug;
	}

	private Student getStudent(AreteResponseDTO response, Course course, Slug slug) {
		Student student;
		Optional<Student> optionalStudent = studentRepository.findByUniid(response.getUniid());
		student = optionalStudent.orElseGet(() -> Student.builder()
				.uniid(response.getUniid())
				.firstTested(response.getTimestamp())
				.lastTested(response.getTimestamp())
				.build());

		if (student.getGitRepo() == null && response.getGitStudentRepo() != null) {
			student.setGitRepo(response.getGitTestRepo());
		}

		student.getCourses().add(course.getGitUrl());
		student.getSlugs().add(slug.getName());
		return student;
	}

	private void updateStudentSlugCourse(AreteResponseDTO response, Student student, Slug slug, Course course) {

		if (response.getStyle() == 100) {
			slug.setCommitsStyleOK(slug.getCommitsStyleOK() + 1);
			course.setCommitsStyleOK(course.getCommitsStyleOK() + 1);
			student.setCommitsStyleOK(student.getCommitsStyleOK() + 1);
		}

		int newDiagnosticErrors = response.getErrors().size();

		int newTestErrors = 0;
		int newTestPassed = 0;
		int newTestsRan = 0;

		Map<String, Integer> testErrors = new HashMap<>();

		for (TestContextDTO TestContextDTO : response.getTestSuites()) {
			for (UnitTestDTO UnitTestDTO : TestContextDTO.getUnitTests()) {
				newTestsRan += 1;
				if (UnitTestDTO.getStatus().equals(TestStatus.FAILED)) {
					newTestErrors += 1;
					if (testErrors.containsKey(UnitTestDTO.getExceptionClass())) {
						testErrors.put(UnitTestDTO.getExceptionClass(), testErrors.get(UnitTestDTO.getExceptionClass()) + 1);
					} else {
						testErrors.put(UnitTestDTO.getExceptionClass(), 1);
					}
				}
				if (UnitTestDTO.getStatus().equals(TestStatus.PASSED)) {
					newTestPassed += 1;
				}
			}
		}

		slug.setTotalCommits(slug.getTotalCommits() + 1);
		course.setTotalCommits(course.getTotalCommits() + 1);
		student.setTotalCommits(student.getTotalCommits() + 1);

		slug.setTotalDiagnosticErrors(slug.getTotalDiagnosticErrors() + newDiagnosticErrors);
		course.setTotalDiagnosticErrors(course.getTotalDiagnosticErrors() + newDiagnosticErrors);
		student.setTotalDiagnosticErrors(student.getTotalDiagnosticErrors() + newDiagnosticErrors);

		slug.setTotalTestErrors(slug.getTotalTestErrors() + newTestErrors);
		course.setTotalTestErrors(course.getTotalTestErrors() + newTestErrors);
		student.setTotalTestErrors(student.getTotalTestErrors() + newTestErrors);

		slug.setTotalTestsPassed(slug.getTotalTestsPassed() + newTestPassed);
		course.setTotalTestsPassed(course.getTotalTestsPassed() + newTestPassed);
		student.setTotalTestsPassed(student.getTotalTestsPassed() + newTestPassed);

		slug.setTotalTestsRan(slug.getTotalTestsRan() + newTestsRan);
		course.setTotalTestsRan(course.getTotalTestsRan() + newTestsRan);
		student.setTotalTestsRan(student.getTotalTestsRan() + newTestsRan);

		student.getTimestamps().add(response.getTimestamp());
		student.setLastTested(response.getTimestamp());


		student.setDifferentCourses(student.getCourses().size());
		student.setDifferentSlugs(student.getSlugs().size());

		updateCourse(course, course.getId());
		updateSlug(slug, slug.getId());
		updateStudent(student, student.getId());

	}

	public void updateSubmissions(Submission submission, String hash) {
		logger.info("Updating submission cache: {}", hash);
		submissionRepository.saveAndFlush(submission);
		cacheService.updateSubmissionList(submission);
	}

	public Course updateCourse(Course course, Long id) {
		logger.info("Updating course cache: {}", id);
		courseRepository.saveAndFlush(course);
		cacheService.updateCourseList(course);
		return course;
	}

	public Slug updateSlug(Slug slug, Long id) {
		logger.info("Updating slug cache: {}", id);
		slugRepository.saveAndFlush(slug);
		cacheService.updateSlugList(slug);
		return slug;
	}

	public Student updateStudent(Student student, Long id) {
		logger.info("Updating student cache: {}", id);
		studentRepository.saveAndFlush(student);
		cacheService.updateStudentList(student);
		return student;
	}

	public AreteResponseDTO makeRequestSync(AreteRequestDTO areteRequest) {
		logger.info("Forwarding a sync submission: {}", areteRequest);
		return areteClient.requestSync(areteRequest);
	}

	public void makeRequestWebhook(AreteTestUpdateDTO update, String testRepository) {
		CommitDTO latest = update.getCommits().get(0);

		Set<String> slugs = new HashSet<>();
		slugs.addAll(latest.getAdded());
		slugs.addAll(latest.getModified());

		AreteRequestDTO request = AreteRequestDTO.builder()
				.eMail(latest.getAuthor().getEmail())
				.uniid(latest.getAuthor().getName())
				.gitStudentRepo(update.getProject().getUrl())
				.gitTestRepo(testRepository)
				.slugs(slugs)
				.build();

		makeRequestAsync(request);
	}

	public void makeRequestAsync(AreteRequestDTO areteRequest) {
		logger.info("Forwarding a async submission: {}", areteRequest);
		areteClient.requestAsync(areteRequest);
	}

	public void updateImage(String image) {
		logger.info("Updating image: {}", image);
		areteClient.updateImage(image);
	}

	public void updateTests(AreteTestUpdateDTO areteTestUpdate) {
		logger.info("Updating tests: {}", areteTestUpdate);
		areteClient.updateTests(areteTestUpdate);
	}

	public SystemStateDTO getTesterState() {
		logger.info("Reading tester state");
		return areteClient.requestState();
	}

}
