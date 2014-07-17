require 'rake'

task :default => :test
task :test => :features

desc 'Test all features'
begin
  require 'cucumber'
  require 'cucumber/rake/task'
  Cucumber::Rake::Task.new(:features)
rescue LoadError
  task :features do
    $stderr.puts 'Please run: `bundle install` to install required gems.'
  end
end
