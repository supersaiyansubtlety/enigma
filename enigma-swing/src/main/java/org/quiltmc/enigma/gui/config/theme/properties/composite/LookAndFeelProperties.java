package org.quiltmc.enigma.gui.config.theme.properties.composite;

import org.quiltmc.config.api.Config;
import org.quiltmc.config.api.annotations.Comment;
import org.quiltmc.config.api.values.TrackedValue;
import org.quiltmc.enigma.gui.config.theme.ThemeUtil;
import org.quiltmc.enigma.gui.config.theme.properties.ThemeProperties;

import java.util.function.Consumer;
import java.util.stream.Stream;

public class LookAndFeelProperties implements ConfigurableConfigCreator {
	public final Colors colors;

	public LookAndFeelProperties() {
		this.colors = this.buildLookAndFeelColors(new Colors.Builder()).build();
	}

	@Override
	public void create(Config.Builder builder) {
		builder.metadata(Comment.TYPE, ThemeProperties::addColorFormatComment);
		builder.section("look_and_feel_colors", this.colors);
	}

	@Override
	public void configure() {
		this.colors.configure();
	}

	protected Colors.Builder buildLookAndFeelColors(Colors.Builder lookAndFeelColors) {
		// start with default (light) colors
		return lookAndFeelColors;
	}

	public static class Colors implements Consumer<Config.SectionBuilder> {
		public final TrackedValue<ThemeProperties.SerializableColor> foreground;
		public final TrackedValue<ThemeProperties.SerializableColor> background;

		public final TrackedValue<ThemeProperties.SerializableColor> accentBaseColor;

		public final TrackedValue<ThemeProperties.SerializableColor> activeCaption;
		public final TrackedValue<ThemeProperties.SerializableColor> inactiveCaption;

		public final TrackedValue<ThemeProperties.SerializableColor> errorBorder;

		public final TrackedValue<ThemeProperties.SerializableColor> warningBorder;

		private Colors(
				ThemeProperties.SerializableColor foreground,
				ThemeProperties.SerializableColor background,

				ThemeProperties.SerializableColor accentBaseColor,

				ThemeProperties.SerializableColor activeCaption,
				ThemeProperties.SerializableColor inactiveCaption,

				ThemeProperties.SerializableColor errorBorder,
				ThemeProperties.SerializableColor warningBorder
		) {
			this.foreground = TrackedValue.create(foreground, "foreground");
			this.background = TrackedValue.create(background, "background");

			this.accentBaseColor = TrackedValue.create(accentBaseColor, "accentBaseColor");

			this.activeCaption = TrackedValue.create(activeCaption, "activeCaption");
			this.inactiveCaption = TrackedValue.create(inactiveCaption, "inactiveCaption");

			this.errorBorder = TrackedValue.create(errorBorder, "errorBorder");
			this.warningBorder = TrackedValue.create(warningBorder, "warningBorder");
		}

		public void configure() {
			this.stream().forEach(ThemeUtil::resetIfAbsent);
		}

		public Stream<TrackedValue<ThemeProperties.SerializableColor>> stream() {
			return Stream.of(
				this.foreground,
				this.background,

				this.accentBaseColor,
				this.activeCaption,
				this.inactiveCaption,

				this.errorBorder,
				this.warningBorder
			);
		}

		public TrackedValue<ThemeProperties.SerializableColor> getWarningBorder() {
			return this.warningBorder;
		}

		public TrackedValue<ThemeProperties.SerializableColor> getErrorBorder() {
			return this.errorBorder;
		}

		public TrackedValue<ThemeProperties.SerializableColor> getInactiveCaption() {
			return this.inactiveCaption;
		}

		public TrackedValue<ThemeProperties.SerializableColor> getActiveCaption() {
			return this.activeCaption;
		}

		public TrackedValue<ThemeProperties.SerializableColor> getAccentBaseColor() {
			return this.accentBaseColor;
		}

		public TrackedValue<ThemeProperties.SerializableColor> getBackground() {
			return this.background;
		}

		public TrackedValue<ThemeProperties.SerializableColor> getForeground() {
			return this.foreground;
		}

		@Override
		public void accept(Config.SectionBuilder builder) {
			this.stream().forEach(builder::field);
		}

		// default colors are from FlatLightLaf.properties
		public static class Builder {
			private ThemeProperties.SerializableColor foreground = new ThemeProperties.SerializableColor(0xFF000000);
			private ThemeProperties.SerializableColor background = new ThemeProperties.SerializableColor(0xFFF2F2F2);

			private ThemeProperties.SerializableColor accentBaseColor = new ThemeProperties.SerializableColor(0xFF2675BF);

			private ThemeProperties.SerializableColor activeCaption = new ThemeProperties.SerializableColor(0xFF99B4D1);
			private ThemeProperties.SerializableColor inactiveCaption = new ThemeProperties.SerializableColor(0xFFBFCDDB);

			private ThemeProperties.SerializableColor errorBorder = new ThemeProperties.SerializableColor(0xFFE53E4D);
			private ThemeProperties.SerializableColor warningBorder = new ThemeProperties.SerializableColor(0xFFE2A53A);

			public Colors build() {
				return new Colors(
					this.foreground,
					this.background,

					this.accentBaseColor,

					this.activeCaption,
					this.inactiveCaption,

					this.errorBorder,
					this.warningBorder
				);
			}

			public Builder foreground(ThemeProperties.SerializableColor foreground) {
				this.foreground = foreground;
				return this;
			}

			public Builder background(ThemeProperties.SerializableColor background) {
				this.background = background;
				return this;
			}

			public Builder accentBaseColor(ThemeProperties.SerializableColor accentBaseColor) {
				this.accentBaseColor = accentBaseColor;
				return this;
			}

			public Builder activeCaption(ThemeProperties.SerializableColor activeCaption) {
				this.activeCaption = activeCaption;
				return this;
			}

			public Builder inactiveCaption(ThemeProperties.SerializableColor inactiveCaption) {
				this.inactiveCaption = inactiveCaption;
				return this;
			}

			public Builder errorBorder(ThemeProperties.SerializableColor errorBorder) {
				this.errorBorder = errorBorder;
				return this;
			}

			public Builder warningBorder(ThemeProperties.SerializableColor warningBorder) {
				this.warningBorder = warningBorder;
				return this;
			}
		}
	}
}
