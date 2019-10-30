package org.publichealthbioinformatics.irida.plugin.snippy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.PostProcessingException;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplateField;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.PipelineProvidedMetadataEntry;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.type.AnalysisType;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.pipeline.results.updater.AnalysisSampleUpdater;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;

/**
 * This implements a class used to perform post-processing on the analysis
 * pipeline results to extract information to write into the IRIDA metadata
 * tables. Please see
 * <https://github.com/phac-nml/irida/blob/development/src/main/java/ca/corefacility/bioinformatics/irida/pipeline/results/AnalysisSampleUpdater.java>
 * or the README.md file in this project for more details.
 */
public class SnippyPluginUpdater implements AnalysisSampleUpdater {

	private final MetadataTemplateService metadataTemplateService;
	private final SampleService sampleService;
	private final IridaWorkflowsService iridaWorkflowsService;

	/**
	 * Builds a new {@link SnippyPluginUpdater} with the given services.
	 * 
	 * @param metadataTemplateService The metadata template service.
	 * @param sampleService           The sample service.
	 * @param iridaWorkflowsService   The irida workflows service.
	 */
	public SnippyPluginUpdater(MetadataTemplateService metadataTemplateService, SampleService sampleService,
							   IridaWorkflowsService iridaWorkflowsService) {
		this.metadataTemplateService = metadataTemplateService;
		this.sampleService = sampleService;
		this.iridaWorkflowsService = iridaWorkflowsService;
	}

	/**
	 * Code to perform the actual update of the {@link Sample}s passed in the
	 * collection.
	 * 
	 * @param samples  A collection of {@link Sample}s that were passed to this
	 *                 pipeline.
	 * @param analysisSubmission The {@link AnalysisSubmission} object corresponding to this
	 *                 analysis pipeline.
	 */
	@Override
	public void update(Collection<Sample> samples, AnalysisSubmission analysisSubmission) throws PostProcessingException {
		if (samples == null) {
			throw new IllegalArgumentException("samples is null");
		} else if (analysisSubmission == null) {
			throw new IllegalArgumentException("analysisSubmission is null");
		}

		// extracts paths to the analysis result files
		AnalysisOutputFile treeAnalysisFile = analysisSubmission.getAnalysis().getAnalysisOutputFile("tree.nhx");
		Path treeFile = treeAnalysisFile.getFile();

		for (Sample sample:samples) {
			try {
				Map<String, MetadataEntry> metadataEntries = new HashMap<>();

				// get information about the workflow (e.g., version and name)
				IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysisSubmission.getWorkflowId());
				String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();
				String workflowName = iridaWorkflow.getWorkflowDescription().getName();

				// gets information from the "tree.nhx" output file and constructs metadata
				// objects
				Map<String, String> treeValues = parseTreeFile(treeFile);
				for (String treeKey : treeValues.keySet()) {
					final String treeValue = treeValues.get(treeKey);

					PipelineProvidedMetadataEntry treeEntry = new PipelineProvidedMetadataEntry(treeValue, "text",
							analysisSubmission);

					// key will be string like 'snippy/xxx'
					String key = workflowName.toLowerCase() + "/" + treeKey;
					metadataEntries.put(key, treeEntry);
				}

				Map<MetadataTemplateField, MetadataEntry> metadataMap = metadataTemplateService
						.getMetadataMap(metadataEntries);

				// merges with existing sample metadata
				sample.mergeMetadata(metadataMap);

				// does an update of the sample metadata
				sampleService.updateFields(sample.getId(), ImmutableMap.of("metadata", sample.getMetadata()));
			} catch (IOException e) {
				throw new PostProcessingException("Error parsing hash file", e);
			} catch (IridaWorkflowNotFoundException e) {
				throw new PostProcessingException("Could not find workflow for id=" + analysisSubmission.getWorkflowId(), e);
			}
		}
	}

	/**
	 * Parses out values from the hash file into a {@link Map} linking 'hashType' to
	 * 'hashValue'.
	 * 
	 * @param treeFile The {@link Path} to the file containing the tree from
	 *                 the pipeline. This file should contain contents like:
	 * 
	 *                 <pre>
	 *
	 *                 </pre>
	 * 
	 * @return A {@link Map} linking '?' to '?'.
	 * @throws IOException             If there was an error reading the file.
	 * @throws PostProcessingException If there was an error parsing the file.
	 */
	private Map<String, String> parseTreeFile(Path treeFile) throws IOException, PostProcessingException {
		Map<String, String> treeValues = new HashMap<>();

		BufferedReader treeReader = new BufferedReader(new FileReader(treeFile.toFile()));

		try {
			assert true; // just a placeholder, not actually parsing tree
		} finally {
			// make sure to close, even in cases where an exception is thrown
			treeReader.close();
		}

		return treeValues;
	}

	/**
	 * The {@link AnalysisType} this {@link AnalysisSampleUpdater} corresponds to.
	 * 
	 * @return The {@link AnalysisType} this {@link AnalysisSampleUpdater}
	 *         corresponds to.
	 */
	@Override
	public AnalysisType getAnalysisType() {
		return SnippyPlugin.SNIPPY_SINGLE;
	}
}
