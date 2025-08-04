package org.quiltmc.enigma.name_proposal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quiltmc.enigma.TestUtil;
import org.quiltmc.enigma.api.Enigma;
import org.quiltmc.enigma.api.EnigmaPlugin;
import org.quiltmc.enigma.api.EnigmaPluginContext;
import org.quiltmc.enigma.api.EnigmaProfile;
import org.quiltmc.enigma.api.EnigmaProject;
import org.quiltmc.enigma.api.ProgressListener;
import org.quiltmc.enigma.api.analysis.index.jar.EntryIndex;
import org.quiltmc.enigma.api.analysis.index.jar.JarIndex;
import org.quiltmc.enigma.api.class_provider.ClasspathClassProvider;
import org.quiltmc.enigma.api.service.NameProposalService;
import org.quiltmc.enigma.api.source.TokenType;
import org.quiltmc.enigma.api.translation.mapping.EntryMapping;
import org.quiltmc.enigma.api.translation.mapping.EntryRemapper;
import org.quiltmc.enigma.api.translation.representation.entry.Entry;
import org.quiltmc.enigma.api.translation.representation.entry.LocalVariableEntry;
import org.tinylog.Logger;

import javax.annotation.Nullable;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestDynamicParamNameProposal {
	private static final Path JAR = TestUtil.obfJar("complete");
	private static EnigmaProject project;

	@BeforeAll
	public static void setupEnigma() {
		Reader configReader = new StringReader(
				"""
				{
					"services": {
						"name_proposal": [
							{
								"id": "%s"
							}
						]
					}
				}
				""".formatted(TestPlugin.TestDynamicParamNameProposer.ID)
		);

		try {
			EnigmaProfile profile = EnigmaProfile.parse(configReader);
			Enigma enigma = Enigma.builder().setProfile(profile).setPlugins(List.of(new TestPlugin())).build();
			project = enigma.openJar(JAR, new ClasspathClassProvider(), ProgressListener.createEmpty());
		} catch (Exception e) {
			Logger.error(e, "Failed to open jar!");
		}
	}

	@Test
	public void testDynamicParamNameProposal() {
		final EntryIndex entryIndex = project.getJarIndex().getIndex(EntryIndex.class);

		project.getRemapper().insertDynamicallyProposedMappings(null, null, null);

		{ // DEBUG
			final Map<Boolean, List<LocalVariableEntry>> paramProposals = entryIndex.getMethods().stream()
				.flatMap(method -> method.getParameters(entryIndex).stream())
				.collect(Collectors.partitioningBy(param -> {
					final EntryMapping mapping = project.getRemapper().getMapping(param);

					return mapping.tokenType().isProposed();
				}));

			Logger.error("Params proposed: " + paramProposals.get(true).size());
			Logger.error("Params not proposed: " + paramProposals.get(false).size());
		}

		entryIndex.getMethods().stream()
			.flatMap(method -> method.getParameters(entryIndex).stream())
			.forEach(param -> {
				final EntryMapping mapping = project.getRemapper().getMapping(param);

				Assertions.assertTrue(mapping.tokenType().isProposed(), "Missing param proposal for " + param);

				Assertions.assertEquals(
					TestPlugin.TestDynamicParamNameProposer.ID, mapping.sourcePluginId(),
					"Expected %s to propose param name, but %s did!"
						.formatted(TestPlugin.TestDynamicParamNameProposer.ID, mapping.sourcePluginId())
				);
			});
	}

	private static class TestPlugin implements EnigmaPlugin {
		@Override
		public void init(EnigmaPluginContext ctx) {
			ctx.registerService(NameProposalService.TYPE, ignored -> new TestDynamicParamNameProposer());
		}

		static class TestDynamicParamNameProposer implements NameProposalService {
			static final String ID = "test:dynamic_param_names";

			@Override
			public Map<Entry<?>, EntryMapping> getProposedNames(Enigma enigma, JarIndex index) {
				return null;
			}

			@Override
			public Map<Entry<?>, EntryMapping> getDynamicProposedNames(
					EntryRemapper remapper, @Nullable Entry<?> obfEntry,
					@Nullable EntryMapping oldMapping, @Nullable EntryMapping newMapping
			) {
				final EntryIndex entryIndex = remapper.getJarIndex().getIndex(EntryIndex.class);

				return entryIndex.getMethods().stream()
					.flatMap(method -> {
						AtomicInteger count = new AtomicInteger(0);
						return method.getParameters(entryIndex).stream()
							.map(param -> Map.entry(
								param,
								new EntryMapping(
									method.getSimpleName() + "_arg" + count.getAndIncrement(),
									null, TokenType.DYNAMIC_PROPOSED, ID
								))
							);
					})
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			}

			@Override
			public String getId() {
				return ID;
			}
		}
	}
}
