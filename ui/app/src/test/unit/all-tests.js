import 'common/helpers/configure';
import 'common/helpers/utils';
import 'unit/helpers/configure';

var testsContext = require.context('.', true, /Test\.js$/);
testsContext.keys().forEach(testsContext);
