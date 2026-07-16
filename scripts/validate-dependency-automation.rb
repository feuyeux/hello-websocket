#!/usr/bin/env ruby
# frozen_string_literal: true

require "yaml"

ROOT = File.expand_path("..", __dir__)
CONFIG = File.join(ROOT, ".github", "dependabot.yml")

expected = {
  ["maven", "/hello-websocket-java"] => "pom.xml",
  ["gradle", "/hello-websocket-kotlin"] => "build.gradle.kts",
  ["gomod", "/hello-websocket-go"] => "go.mod",
  ["cargo", "/hello-websocket-rust"] => "Cargo.toml",
  ["npm", "/hello-websocket-nodejs"] => "package.json",
  ["npm", "/hello-websocket-ts"] => "package.json",
  ["pub", "/hello-websocket-dart"] => "pubspec.yaml",
  ["composer", "/hello-websocket-php"] => "composer.json",
  ["pip", "/hello-websocket-python"] => "requirements.txt",
  ["nuget", "/hello-websocket-csharp/test"] => "hello-websocket-csharp-test.csproj",
  ["swift", "/hello-websocket-swift"] => "Package.swift",
  ["docker", "/docker"] => "Dockerfile.java",
  ["github-actions", "/"] => ".github/workflows/build-test.yml"
}

data = YAML.safe_load_file(CONFIG, aliases: false)
abort "dependabot.yml must use version 2" unless data["version"] == 2
updates = data.fetch("updates")
keys = updates.map { |entry| [entry["package-ecosystem"], entry["directory"]] }

duplicates = keys.tally.select { |_key, count| count > 1 }.keys
abort "duplicate Dependabot entries: #{duplicates.inspect}" unless duplicates.empty?

missing = expected.keys - keys
unexpected = keys - expected.keys
abort "missing Dependabot entries: #{missing.inspect}" unless missing.empty?
abort "unexpected Dependabot entries: #{unexpected.inspect}" unless unexpected.empty?

expected.each do |(ecosystem, directory), manifest|
  path = File.join(ROOT, directory.sub(%r{^/}, ""), manifest)
  abort "missing #{ecosystem} manifest: #{path}" unless File.file?(path)
end

updates.each do |entry|
  schedule = entry.fetch("schedule")
  abort "all updates must run weekly" unless schedule["interval"] == "weekly"
  abort "all updates must run on Monday" unless schedule["day"] == "monday"
  abort "all updates must use Asia/Shanghai" unless schedule["timezone"] == "Asia/Shanghai"
  abort "missing update time" unless schedule["time"].match?(/\A\d{2}:\d{2}\z/)
  abort "all updates must target main" unless entry["target-branch"] == "main"
  abort "all updates must auto-rebase" unless entry["rebase-strategy"] == "auto"
  groups = entry.fetch("groups").values
  abort "expected one non-major group for #{entry["package-ecosystem"]}" unless groups.length == 1
  group = groups.first
  abort "non-major group must cover all dependencies" unless group["patterns"] == ["*"]
  abort "non-major group must cover minor and patch updates" unless group["update-types"].sort == %w[minor patch]
  abort "open-pull-requests-limit must be positive" unless entry["open-pull-requests-limit"].to_i.positive?
end

docker_languages = %w[cpp csharp dart go java kotlin node php python rust swift ts]
dockerfiles = Dir.glob(File.join(ROOT, "docker", "Dockerfile.*"))
dockerfile_names = dockerfiles.map { |path| File.basename(path) }.sort
expected_dockerfile_names = docker_languages.map { |language| "Dockerfile.#{language}" }.sort
abort "unexpected Dockerfile set: #{dockerfile_names.inspect}" unless dockerfile_names == expected_dockerfile_names

lockfiles = %w[
  hello-websocket-go/go.sum
  hello-websocket-rust/Cargo.lock
  hello-websocket-nodejs/package-lock.json
  hello-websocket-ts/package-lock.json
  hello-websocket-dart/pubspec.lock
  hello-websocket-php/composer.lock
  hello-websocket-csharp/test/packages.lock.json
  hello-websocket-kotlin/client/gradle.lockfile
  hello-websocket-kotlin/common/gradle.lockfile
  hello-websocket-kotlin/server/gradle.lockfile
]
missing_locks = lockfiles.reject { |path| File.file?(File.join(ROOT, path)) }
abort "missing lockfiles: #{missing_locks.join(", ")}" unless missing_locks.empty?

puts "Dependency automation coverage is complete (#{updates.length} update targets, #{dockerfiles.length} Dockerfiles)."
