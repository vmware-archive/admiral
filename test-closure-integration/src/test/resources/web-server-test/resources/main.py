def test():
	print('Faked mehtod')

def handler(ctx):
    inputs = ctx['inputs']
    print('Hello string: '.format(inputs['a']))
    ctx['outputs']['result'] = inputs['a'] + "c"
